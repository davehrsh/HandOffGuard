# HandoffGuard v1.0 release checklist

## Verified — November 16, 2026

- [x] Live Salesforce Flow transfer marker and 60-second timeout result in Developer Edition.
- [x] Separate simulator and protected Salesforce API.
- [x] Java 21 CI regression, Docker build, Cloud setup script syntax and history viewer syntax passed on merged pull request #2.
- [x] Salesforce ID correlation handles 15-character Flow values and 18-character REST records.
- [x] **ACCEPTED_ON_TIME** with actual Salesforce Flow timestamp: session `0Mwg7000002Y5yb`, marker `a00g700002Y7eqXAAR`, human work `0Bzg7000002Y0cMCAS`, latency **12 seconds**, transfer start `2026-11-16T07:14:08Z`.
- [x] **TIMED_OUT** with actual Salesforce Flow timestamp: session `0Mwg7000002Y6Mn`, marker `a00g700002Y7mxlAAB`, start `2026-11-16T07:15:33Z`, no permitted human acceptance as of `2026-11-16T07:19:56.691794651Z`.
- [x] Cloud Run Job `handoffguard-monitor-rhtnq` executed successfully.
- [x] Scheduler configuration enabled at 10-minute intervals; screenshot showed a Cloud Scheduler-initiated successful Cloud Run Job and saved GCS object.
- [x] Private GCS snapshot `snapshots/2026-11-16T07-19-56.691794651Z.json` contains two assessments: one accepted on time, one timed out.
- [x] Cloud Logging exposes structured `HANDOFFGUARD_MONITOR_SUMMARY` breach events with `breached=1`; logs filter matched two events.
- [x] Cloud Monitoring **log alert configuration prepared** in UI: filter on event and `breached>0`; notification minimum 1 hour, autoclose 7 days.
- [x] MonitoringJob, local-only private snapshot viewer, IAM setup script, operational docs and portfolio README committed.

## Still awaiting external confirmation

- [ ] Cloud Monitoring alert policy successfully **saved and enabled**.
- [ ] Email notification channel chosen, confirmed and attached to policy.
- [ ] **Actual email notification delivered** following a new matching breach event.
- [ ] Private `/history` viewer manually tested with downloaded live snapshot JSON.
- [ ] Latest portfolio README updated with this live evidence (see current GitHub commit below; this checkbox can be checked after review).

## Evidence and qualification

The two-session result is **controlled test evidence**, not a representative success-rate sample. `TIMED_OUT` means no permitted human acceptance was observed by the snapshot timestamp, not that acceptance was permanently impossible. A 10-minute polling schedule does **not** guarantee alert delivery within 60 seconds. Cloud Monitoring's one-hour frequency suppresses repeated notifications but does not deduplicate per session.

Source: user-supplied Cloud Shell output and Cloud Logging screenshots, November 16, 2026. Review exact cloud execution records and snapshot files in the project for operational evidence. Do not mark alert delivery complete without proof.
