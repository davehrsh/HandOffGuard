# Salesforce evidence verifier (v0.2)

This version integrates the Java acceptance verifier and adds a **read-only endpoint** `/api/salesforce/evidence` displaying historical, manually recorded Salesforce observations beside the existing simulator UI. No live Salesforce authentication or collection is performed.

## Safety-critical verification contract

Use the actual transfer-request time, not MessagingSession.CreatedDate. `AgentWork.AcceptDateTime` is the acceptance time; exclude Automated Process and Einstein users from permitted human IDs. Timeout requires an observation at or beyond the deadline **and** a complete correlated AgentWork snapshot. An accepted session remains accepted after Ended/Closed. An acceptance after the deadline is ACCEPTED_LATE, even if the session ultimately succeeds.

The historical MS-00000007 evidence establishes 44 seconds from *AgentWork.RequestDateTime* to human acceptance, not an independently verified AI transfer-start SLA. MS-00000008 was manually observed waiting while the human was offline, but lacks precise transfer and observation timestamps; it must not be classified as machine-verified TIMED_OUT.

The verifier's record-based Java API is currently exercised by regression fixtures; the UI displays the curated evidence without falsely assigning automated verdicts. A future collector must provide trustworthy transfer start, observation time, provenance, and snapshot completeness before connecting the two.

## Run

`sh scripts/test.sh` then `sh scripts/run.sh` from project root (Java 21). Open http://localhost:8080.
