# HandoffGuard v1.0.0 — Release Notes

**Release target:** November 16, 2026

## What shipped

- Java 21 handoff-verification engine with deterministic regression tests and Docker deployment.
- Salesforce Agentforce / Omni-Channel Flow marker integration using an actual Flow-generated transfer timestamp, session ID correlation and Salesforce AgentWork human acceptance.
- Protected Salesforce inspection and 60-second SLA monitoring endpoints, with permitted human identity allowlisting and evidence provenance.
- Cloud Run Job, Cloud Scheduler recurring execution (configured every 10 minutes), structured Cloud Logging breach events, private GCS JSON snapshots and local-only history viewer.
- Google Cloud Monitoring log alert policy, with successfully delivered email notification.
- Standalone six-scenario simulator showing corrected configuration versus intentionally broken behavior.
- Private integration and operational runbooks, architecture diagram and evidence-backed release checklist.

## Demonstrated outcomes (controlled Salesforce Developer Edition)

| Session | Verified result | Start (UTC) | Notes |
| --- | --- | --- | --- |
| `0Mwg7000002Y5yb` | `ACCEPTED_ON_TIME` | Nov 16 07:14:08 | Accepted by allowlisted human in **12 seconds** |
| `0Mwg7000002Y6Mn` | `TIMED_OUT` | Nov 16 07:15:33 | No permitted human acceptance as of 07:19:56 UTC |
| `0Mwg7000002Y2cX` | `TIMED_OUT` | Nov 16 05:50:44 | First live 60-second marker-based negative test |

A scheduled Cloud Run execution saved the two-session snapshot to a private bucket; the historical evidence viewer imported and rendered that snapshot successfully. The log alert emitted an **Error** email received at **1:08 PM IST** (incident began 07:38 UTC).

## CI / validation

Java 21 regression suites, 6 retail simulator scenarios, 11 transfer-monitor scenarios, 8 Salesforce verifier regression cases, Docker image compilation, monitoring script syntax and viewer JS parsing checks passed in GitHub Actions. Do not confuse simulator metrics with real Salesforce measurements.

## Usage

- Simulator: https://handoffguard-614664795142.asia-south1.run.app/
- Private evidence viewer: https://handoffguard-614664795142.asia-south1.run.app/history
- Architecture: [docs/ARCHITECTURE.md](ARCHITECTURE.md)
- Operations: [docs/MONITORING_OPERATIONS.md](MONITORING_OPERATIONS.md)
- Verification: [docs/V1_RELEASE_CHECKLIST.md](V1_RELEASE_CHECKLIST.md)

## Limits and security

This is an **independent portfolio demonstration** built on a Developer Edition organization; it is not a Salesforce product or a production-ready service. The public site offers deterministic simulation; it does not expose Salesforce credentials. Source and protected endpoints must not be made public until secrets, user IDs and other org-specific information are reviewed. Cloud Run polling every 10 minutes does not provide a 60-second notification SLA. Alert deduplication and per-attempt correlation are future improvements.
