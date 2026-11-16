# HandoffGuard architecture

This diagram describes the deployed v1.0 control flow. The public deterministic simulator and the private Salesforce evidence path are **separate**.

```mermaid
flowchart TB
    C["Customer requests human support"] --> A["Salesforce Agentforce"]
    A --> F["Omni-Channel escalation Flow"]
    F --> M[("HandoffGuardTransfer__c<br/>Session ID + Flow timestamp")]
    F --> Q["Retail Support routing"]
    Q --> W["Salesforce AgentWork<br/>human acceptance event"]
    M --> J["HandoffGuard Java 21 verifier<br/>Cloud Run Job + protected API"]
    W --> J
    H["Allowlisted human user IDs"] --> J
    J --> D{"Acceptance within 60 s?"}
    D -->|"yes"| P["ACCEPTED_ON_TIME"]
    D -->|"late or missing"| B["ACCEPTED_LATE / TIMED_OUT"]
    D -->|"ambiguous"| I["INSUFFICIENT_EVIDENCE"]
    S["Cloud Scheduler<br/>every 10 minutes"] --> J
    J --> G[("Private Cloud Storage<br/>timestamped JSON snapshots")]
    G --> V["Local-only snapshot viewer<br/>manual file import"]
    J --> L["Cloud Logging<br/>HANDOFFGUARD_MONITOR_SUMMARY"]
    L --> E["Cloud Monitoring<br/>email breach alert"]

    subgraph Public["Public demo (no Salesforce credentials)"]
      X["Deterministic retail simulator<br/>6 scenarios"] --> Y["Corrected and broken configuration evidence"]
    end

    classDef private fill:#e5f2ed,stroke:#427b63,color:#133d30
    classDef public fill:#eef2f7,stroke:#7a91a6,color:#233346
    class M,W,J,G,L private
    class X,Y public
```

## Trust and timing boundaries

- **The 60-second threshold is the acceptance SLA, not a guarantee of alert delivery latency.** The scheduler polls every ten minutes; end-to-end notification can take longer.
- **Provenance:** Salesforce Flow captures `$Flow.CurrentDateTime` at escalation; AgentWork supplies actual acceptance. The verifier excludes automated process users.
- **Correlation:** matching accepts Salesforce 15- and 18-character session IDs. Ambiguous multiple markers are not silently matched to a work event.
- **Data minimization:** no customer transcripts; snapshot viewer parses user-selected JSON locally; real Salesforce endpoints require an API key and must not be called from public JS.
- **Failure behavior:** the Flow's fault connector continues human routing if transfer-marker logging fails; missing evidence cannot be interpreted as a successful handoff.
- **Operational limitations:** monitoring scans recent markers with a cap, and alerts may repeat until sessions age out of that window.
