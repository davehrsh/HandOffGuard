# HandoffGuard v1.0 release checklist

- [x] Live Salesforce Flow transfer marker read and 60-second timeout response confirmed in Developer Edition.
- [x] Separate simulator and protected live API.
- [x] MonitoringJob and private snapshot viewer code committed.
- [ ] CI regression and Docker build passes for latest commit.
- [ ] New controlled human acceptance verified as ACCEPTED_ON_TIME with Flow timestamp.
- [ ] Cloud Run Job executed successfully at least once.
- [ ] Google Cloud Scheduler executes the Cloud Run Job, with private GCS snapshot created.
- [ ] Optional Cloud Logging alert policy / notification channel tested.

Do not mark outstanding items complete without observable evidence. Estimated availability and costs may vary by account.
