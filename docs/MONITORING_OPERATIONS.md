# Automated HandoffGuard monitoring — Google Cloud Run + Scheduler + GCS

## Scope and security

The Cloud Run **web service** remains a public simulator plus API-key-protected Salesforce inspection/monitor endpoints. The **scheduled monitoring** runs as a separate, non-public Cloud Run **Job**. The job pulls live Salesforce records using OAuth client credentials stored in Secret Manager and writes time-stamped, transcript-free snapshots to a **private** Cloud Storage bucket.

The job checks recent Salesforce Flow transfer markers, correlates them with AgentWork, uses only explicitly allowlisted human IDs, and classifies a 60-second transfer deadline. The scheduled task itself runs every **10 minutes**; this is not a 60-second alerting guarantee.

### Requirements

- Salesforce transfer-marker custom object and Flow are active, and the OAuth integration user can read `HandoffGuardTransfer__c`, `MessagingSession`, `AgentWork`.
- A human Salesforce User ID is present in `SF_HUMAN_USER_IDS` on the existing Cloud Run service.
- A successful GitHub-to-Cloud Build deployment of the latest Java image, containing `dev.handoffguard.MonitoringJob`.
- Project: `handoffguard`; region: `asia-south1`.
- The Cloud Shell user has permission to create service accounts, assign per-resource IAM bindings, create Cloud Run jobs and Cloud Scheduler jobs, enable APIs and create Cloud Storage buckets.
- Review Google Cloud billing before creating recurring resources. Cloud Run, Cloud Scheduler, Logging and Storage may incur charges; retention is limited to 30 days.

## One-command setup in Google Cloud Shell

Open https://console.cloud.google.com/?project=handoffguard and activate Cloud Shell.

**Review** [setup-monitoring-gcp.sh](../scripts/setup-monitoring-gcp.sh) before running, particularly its service identity, per-secret permissions, bucket retention and the 10-minute schedule.

```bash
git clone https://github.com/davehrsh/HandOffGuard.git
cd HandOffGuard
bash scripts/setup-monitoring-gcp.sh
```

The repository is private, so Google Cloud Shell must have GitHub access (for example, a GitHub authorized browser, HTTPS personal access token stored outside chat, or Cloud Shell's GitHub authentication). **Never paste a personal access token into ChatGPT or shell history.** If GitHub authentication is unavailable, you can copy the script from the authenticated repository page to Cloud Shell instead.

The script creates or updates:

- `handoffguard-monitor` Cloud Run Job, reusing the current service Docker image.
- `handoffguard-monitor-sa` job identity with individual access to two Secret Manager secrets and **objectCreator** on the GCS bucket.
- Private `gs://handoffguard-snapshots-614664795142` bucket with 30-day lifecycle deletion.
- `handoffguard-scheduler-sa` invocation identity, **Cloud Run Invoker** on the specific job.
- `handoffguard-monitor-10min` Cloud Scheduler HTTP trigger, OAuth-authenticated to the Google Cloud Run Jobs API.

The API key used for the web endpoint is *not* needed by the Cloud Run Job.

## Test the job once before relying on the schedule

```bash
gcloud run jobs execute handoffguard-monitor --project=handoffguard --region=asia-south1 --wait
gcloud logging read 'resource.type="cloud_run_job" AND resource.labels.job_name="handoffguard-monitor"' --project=handoffguard --limit=20 --freshness=1h --format='table(timestamp,severity,textPayload)'
gcloud storage ls gs://handoffguard-snapshots-614664795142/snapshots/
```

If a job fails, inspect the execution logs: https://console.cloud.google.com/run/jobs?project=handoffguard

**Expected:** a JSON snapshot with `generatedAt`, `provenance`, `deadlineSeconds`, `sessionsChecked`, `counts`, and `results`. Every result records the actual Salesforce marker ID and timestamp, acceptance status and evidence classification. No customer transcript or Salesforce token is included.

## Viewing history

1. List objects in the private bucket:
   `gcloud storage ls gs://handoffguard-snapshots-614664795142/snapshots/`
2. Download a selected snapshot through Cloud Shell or the private Google Cloud Storage UI.
3. Open the [HandoffGuard history viewer](https://handoffguard-614664795142.asia-south1.run.app/history), choose the downloaded JSON snapshot.
4. Data is parsed in your browser only; the viewer does not fetch secrets or Salesforce records.

The history page intentionally does not provide public access to private GCS objects.

## Alerting

Each batch emits a Cloud Logging structured event `HANDOFFGUARD_MONITOR_SUMMARY`:

- `severity=ERROR` when any `ACCEPTED_LATE` or `TIMED_OUT` result appears.
- `severity=INFO` otherwise.
- Fields `breached`, `sessionsChecked`, `generatedAt`; no credentials or transcripts.

In Cloud Logging Logs Explorer, use:

```
resource.type="cloud_run_job"
resource.labels.job_name="handoffguard-monitor"
jsonPayload.event="HANDOFFGUARD_MONITOR_SUMMARY"
jsonPayload.severity="ERROR"
```

Create a **log-based alert** using that filter in Google Cloud Console > Logging > Logs Explorer > Create alert, choose your notification channel and test its delivery. Do not assume alert notifications are active until a notification channel and policy are configured and tested. This may repeat alerts for the same still-timed-out session because polling rechecks recent markers.

## Operational limitations

- Scans markers created within approximately the last hour, capped at `MONITOR_MAX_SESSIONS=50` per execution. Burst workloads above the cap can be missed and must be handled with cursor-based paging and durable checkpoints for production.
- Every session may require several Salesforce REST calls; monitor Salesforce API quotas.
- A transfer with multiple markers in the same session returns `INSUFFICIENT_EVIDENCE` rather than calculating a potentially mismatched latency.
- Flow logging failure can leave no marker. The fail-safe Flow routes the customer anyway; absence of the marker cannot be mistaken for a successful handoff. Separate instrumentation-health checks would be needed in production.
- `TIMED_OUT` means no permitted human acceptance in retrieved evidence by observation time. A later acceptance can change the result.
- No automated handoff repair, retry, or assignment happens.
- Cost controls: bucket retention 30 days; scan cap 50; 1 Cloud Run task; 10-minute schedule.
- For a production system, adopt Cloud Run IAM-only service boundaries, Salesforce event streaming, attempt-level identifiers, durable cursoring, retries, and paging.

## Stopping or removing recurring costs

Pause the schedule:

```bash
gcloud scheduler jobs pause handoffguard-monitor-10min --project=handoffguard --location=asia-south1
```

Delete the schedule if no longer needed:

```bash
gcloud scheduler jobs delete handoffguard-monitor-10min --project=handoffguard --location=asia-south1
```

Deleting the schedule stops recurring invocations but does **not** delete the job, bucket, snapshots, or service accounts. Remove those separately if desired.
