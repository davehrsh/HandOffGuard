# Demo video / screenshot guide

A narrated **89-second** HandoffGuard v1.0 evidence walkthrough has been created as a separate media deliverable. It is intentionally not committed to the repository because the screenshots include identifiers from a private Salesforce Developer Edition and Google Cloud project.

For a public-facing demo, capture or redact the following:

1. **Simulator (0–25 s):** Show the six-scenario deterministic test workbench. Clearly label it `SIMULATED ENVIRONMENT`; its latency metrics are not actual Salesforce acceptance latencies.
2. **Architecture (25–37 s):** Explain the real Flow timestamp + AgentWork acceptance correlation, 60-second threshold and human-user allowlist. See [architecture diagram](ARCHITECTURE.md).
3. **Actual evidence (37–52 s):** Use the private history viewer with the downloaded snapshot: one accepted in **12 seconds**, one **TIMED_OUT**. Exclude OAuth tokens, customer transcripts and Salesforce login information.
4. **Cloud automation (52–64 s):** Show the Cloud Run Job / Cloud Scheduler / structured Logs Explorer breach entry. The scheduler runs every ten minutes; it is not instant event monitoring.
5. **Email alert (64–76 s):** Show the verified Google Cloud Monitoring breach notification. Do not reveal private email addresses; if using a recreated card, **label it as a reconstruction**.
6. **Wrap up (76–89 s):** HandoffGuard detects reliability failures; it does not repair routing or guarantee alert delivery in 60 seconds.

Do not publish a live Salesforce chat transcript or private organization information in the repository without a privacy review. Keep the raw screenshot media separate from the open-source code. This repository is currently private.
