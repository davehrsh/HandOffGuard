# Salesforce integration: next milestone

## Required access

An authorized non-production Salesforce org with Agentforce Service Agent, a published Enhanced Web Chat deployment, routing configured to a staffed test queue, and permission to inspect the receiving conversation and related routing/case records. An ordinary developer org must not be assumed to include all entitlements. Verify access in the actual org before spending money or choosing an API implementation.

Do not send passwords or access tokens in chat. Use local environment variables or a secret manager when configuring credentials. Use synthetic conversations in an isolated test environment.

## First real vertical slice

1. Manually demonstrate one customer request → human acceptance in the test web-chat deployment.
2. Identify the receiving-side record/event and the supported API or test-console view that proves acceptance. A routing request or queue assignment is NOT proof that a human joined.
3. Capture a session correlation ID linking the channel transcript and receiving-side evidence. Record timestamps and retain raw artifact references; remove customer personal data.
4. Add a Playwright web-chat driver using selectors discovered from the actual deployment, not invented selectors. Keep the driver independent of the assertion engine.
5. Implement a read-only Salesforce observer using org-verified schemas and least-privilege authentication. Do not invent API paths or field names. Normalize observed facts into Engine.Event.
6. Run HUMAN_REQUEST with real evidence. Then deliberately break the routing in the test org, rerun, and show the failure. Restore the test configuration.
7. Add no-capacity, context and retry scenarios only after their receiving-side signals are reliable.

## Evidence contract

TRANSFER_REQUESTED: channel request to escalate, queue and requestId.
TRANSFER_ACCEPTED: receiving-side acceptance, transferId, destination queue and context actually visible to recipient. Never synthesize this event from "connecting you" text.
QUEUE_UNAVAILABLE: verified unavailable-capacity signal.
CASE_CREATED: actual persisted fallback record and its caseId.
LOOKUP_FAILED: instrumented action failure, not an inferred sentiment.
CUSTOMER_MESSAGE: visible channel message. The normalized kind must be supported by the observed message.

The current TSV importer is a development bridge, not a live connector. Preserve raw evidence separately and define provenance before claiming live validation. Add explicit event IDs to the import schema before using it for high-volume or same-timestamp data.

## Later work

- PostgreSQL run/event storage and migrations; per-org authorization and retention.
- JUnit/Testcontainers integration tests around persistence.
- OpenTelemetry spans around collectors and observation waits, bounded deadlines and backoff.
- Baseline comparisons, JUnit XML reports and configurable release gates.
- AI-assisted scenario generation with human review; deterministic assertions remain authoritative.
- Semantic context validation against expected order ID and issue, rather than mere presence.
- Real p50/p95 measured over repeated runs; separate queue wait from platform/action latency.

A prototype estimate is not a benchmark. Do not claim user counts, latency improvements, Salesforce certification or production reliability from this simulator.
