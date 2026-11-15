# Salesforce Flow transfer-start capture — HandoffGuard

## Why this is needed

Salesforce AgentWork.AcceptDateTime establishes when a human accepted work, **not** when the AI requested a handoff. AgentWork.CreatedDate and PendingServiceRouting.CreatedDate are not universal substitutes for a handoff-request timestamp; routing and transfer paths can affect record creation time. Do not reuse the illustrative 2026-11-14 09:23 UTC timestamp from the initial API connectivity test.

HandoffGuard must measure two separately evidenced events:
1. `transferStartedAt`: the instant the Salesforce Flow entered the escalation / route-to-human branch.
2. `AcceptDateTime`: a real human AgentWork acceptance, correlated to the same MessagingSession.

## One-time Salesforce org setup (manual, no local software)

### A. Create an audit custom object

In **Salesforce Setup > Object Manager > Create > Custom Object**:

- Label: **HandoffGuard Transfer**
- Plural Label: **HandoffGuard Transfers**
- Object Name / API Name: **HandoffGuardTransfer__c**
- Record Name: Auto Number, e.g. `HGT-{000000}` (or a required text/name according to your org)
- Deployment Status: Deployed
- Ensure the OAuth Run As integration user has read access to the object and fields.

Add custom fields:
- **Messaging Session ID**: Text(18), required, API name `Messaging_Session_ID__c`
- **Transfer Started At**: Date/Time, required, API name `Transfer_Started_At__c`

Avoid storing customer messages, order details, phone numbers, secrets or tokens in the audit record.

### B. Instrument the *existing* escalation Flow

Before the element that initiates route-to-human escalation, use Flow Builder's **Create Records** action to insert exactly one `HandoffGuardTransfer__c` record.

- `Messaging_Session_ID__c` = the **real Salesforce MessagingSession.Id** from the running conversation context (do not hardcode a historical ID).
- `Transfer_Started_At__c` = `$Flow.CurrentDateTime` at that escalation branch.
- For repeated handoffs, create separate records; the initial monitor refuses to compute a single-session latency if more than one attempt is present.
- Add a fault path and test to ensure instrumenting the Flow does not accidentally break routing.
- Don't use `MessagingSession.CreatedDate` or `AgentWork.CreatedDate` as a shortcut; neither necessarily identifies the AI-to-human escalation request.

**Important:** You must verify that the escalation Flow actually exposes the `MessagingSession.Id` when the Create Records element executes. If it doesn't, capture and pass the real session ID through the supported routing context before enabling monitoring. If that is not possible in the current org's architecture, stop here and choose a different correlated event source.

Publish/activate the updated Flow only after testing in the Salesforce Developer org.

### C. Grant read access

Grant the External Client App's Run As user object **Read** permission and field-level visibility to `HandoffGuardTransfer__c.Messaging_Session_ID__c` and `Transfer_Started_At__c`. Do not grant write access to the integration user. Flow's execution permissions are separate.

## Enable the read-only Cloud Run collector

After (and only after) the marker records exist and the integration user has read access:

1. In Cloud Run service `handoffguard` (project `handoffguard`, region `asia-south1`), add the regular environment variable `SF_FLOW_MARKERS_ENABLED=true`.
2. Keep existing `SF_LIVE_ENABLED=true`, `SF_HUMAN_USER_IDS`, and the Secret Manager bindings.
3. Deploy a new revision.

Protected API: `POST /api/salesforce/monitor`, body `{"sessionId":"<actual session id>"}`, header `X-HandoffGuard-Key` (the same secret used for `/api/salesforce/inspect`).

Cloud Shell test (do not print the key):

```bash
KEY="$(gcloud secrets versions access latest --secret=handoffguard-api-key --project=handoffguard)"
curl --fail-with-body -sS \
  -X POST https://handoffguard-614664795142.asia-south1.run.app/api/salesforce/monitor \
  -H 'Content-Type: application/json' \
  -H "X-HandoffGuard-Key: $KEY" \
  --data '{"sessionId":"REPLACE_WITH_NEW_SESSION_ID"}'
unset KEY
```

Status meanings:
- `ACCEPTED_ON_TIME` — first permitted human acceptance occurred at most 60 seconds after Flow-captured transfer start.
- `ACCEPTED_LATE` — acceptance occurred more than 60 seconds after transfer start.
- `PENDING` — no acceptance yet and 60-second target has not elapsed.
- `TIMED_OUT` — no permitted human acceptance in retrieved AgentWork as of observation, deadline passed. A later acceptance may still occur.
- `INSUFFICIENT_EVIDENCE` — missing/ambiguous marker, invalid time, missing allowlist, or incomplete record snapshot.

All statuses must retain `SALESFORCE_FLOW_MARKER_AND_AGENTWORK` provenance and a session correlation. For multiple transfer attempts, this first iteration returns `INSUFFICIENT_EVIDENCE` rather than silently matching unrelated attempts.

## Automation boundary

This release supports **on-demand verified handoff SLO assessment** based on an independent Flow marker. It does **not** yet include a persistent event store, recurring Cloud Scheduler job, multi-session monitor, or alert delivery. Those require Cloud resources and service credentials, and should be implemented only after a genuine end-to-end marker test. The public demo dashboard remains simulator-only; never expose Salesforce transcripts or API secrets there.

## Primary documentation

- Salesforce Flow Create Records: https://help.salesforce.com/s/articleView?id=flow_ref_elements_data_create.htm&language=en_US
- Salesforce Flow CurrentDateTime: https://help.salesforce.com/s/articleView?id=flow_ref_resources_system_variables.htm&language=en_US
- Salesforce AgentWork object: https://developer.salesforce.com/docs/platform/object-reference/references/standard-objects/object-agentwork.html
