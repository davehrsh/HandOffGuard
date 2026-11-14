# Salesforce live verification (opt-in)

This integration is read-only. It is **disabled by default** on public Cloud Run services.

## Salesforce configuration

1. In Salesforce Setup, open **External Client App Manager**, create an External Client App for HandoffGuard, and enable OAuth with the **Manage user data via APIs (api)** scope.
2. Enable **Client Credentials Flow** and assign a dedicated integration execution user with minimum necessary read access to MessagingSession and AgentWork. Salesforce's client credentials flow uses your **My Domain** token endpoint.
3. Ensure the integration user can query `MessagingSession.Id` and `AgentWork.Id, WorkItemId, UserId, AcceptDateTime, CreatedDate`.
4. Identify the actual human Salesforce User IDs permitted to accept the handoff. Never include the Automated Process user.
5. Store the consumer secret and verifier API key in **Google Secret Manager** and grant only the Cloud Run service identity access. Do not put secrets in GitHub or screenshots.

## Cloud Run configuration

Set environment variables:
- `SF_LIVE_ENABLED=true`
- `SF_DOMAIN=https://YOUR-MY-DOMAIN.my.salesforce.com`
- `SF_CLIENT_ID` — OAuth consumer key (prefer secret-backed)
- `SF_CLIENT_SECRET` — OAuth consumer secret (Secret Manager)
- `SF_HUMAN_USER_IDS` — comma-separated 15/18-character Salesforce User IDs of real humans
- `SF_VERIFY_API_KEY` — random secret of at least 32 characters (Secret Manager)
- `SF_API_VERSION=v66.0` — optional; change if unsupported by org

The endpoint is `POST /api/salesforce/verify`, requiring header `X-HandoffGuard-Key`. Example request body:

```json
{"sessionId":"0Mwg7000002VwGHCA0","transferStartedAt":"2026-11-14T09:23:00Z"}
```

**The timestamp above is illustrative, not evidence of the actual transfer time.** Supply the real transfer-request timestamp from a trustworthy event source. Never infer it from the MessagingSession creation time.

The API uses the current server observation time, queries the Salesforce session and all related AgentWork records, and returns only the acceptance verdict (not customer transcripts). It verifies a 60-second deadline. A Salesforce REST API error produces HTTP 502; missing setup produces HTTP 503.

### Security and accuracy caveats

- The live verification endpoint is authenticated by an application key, but it is still internet-reachable if the Cloud Run service is public. For production, split the private Salesforce collector into an IAM-protected service, restrict ingress, and use Cloud Run authenticated invocations instead of a shared key.
- **Do not enable live verification until that access model is acceptable.**
- A complete paginated AgentWork query supports a no-acceptance finding at observation time, but data consistency and access permissions must be verified. A missing transfer-start timestamp results in no verdict.
- This is an on-demand check, **not** a background 60-second timer. Automated scheduling and transfer-event ingestion are future work.
- The simulator remains public and independent of Salesforce.


## Read-only inspection without an inferred transfer time (recommended first test)

The protected endpoint `POST /api/salesforce/inspect` takes only `{"sessionId":"0Mwg7000002VwGHCA0"}`. It returns whether a permitted human accepted the work, accepted-at timestamps, AgentWork count, and `sloStatus=NOT_ASSESSED_TRANSFER_START_UNVERIFIED`. It does not return transcripts or credentials.

Cloud Shell connectivity test (this is intentionally read-only; inspect one known synthetic test session):

```bash
KEY="$(gcloud secrets versions access latest --secret=handoffguard-api-key --project=handoffguard)"
curl --fail-with-body -sS -X POST "https://handoffguard-614664795142.asia-south1.run.app/api/salesforce/inspect" \
  -H "Content-Type: application/json" \
  -H "X-HandoffGuard-Key: $KEY" \
  --data '{"sessionId":"0Mwg7000002VwGHCA0"}'
unset KEY
```

The older `/api/salesforce/verify` endpoint still accepts a caller-supplied `transferStartedAt`. Its output now marks the resulting latency/SLO as **not verified**, because arbitrary supplied timestamps do not establish evidence of when escalation began. For a defensible 60-second claim, capture an independently sourced transfer-request event with a timestamp and a stable session correlation ID; then verify chronological ordering and capture source provenance. The date used in the initial connectivity test was illustrative and cannot substantiate the earlier `ACCEPTED_LATE` label.
