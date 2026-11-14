# HandoffGuard

**Did the customer actually reach a human?** A Java reliability workbench that verifies handoff outcomes from event evidence, rather than trusting a chatbot's transfer message.

## Current status

Working local MVP: Java 21 engine, six retail scenarios, fault injection, assertion-level evidence, responsive dashboard, downloadable JSON reports and regression tests. **All dashboard runs are simulations. Salesforce is not connected.** A Spring Boot adapter is included, but was not compiled in the authoring environment because Maven Central was unreachable; the dependency-free Java version was compiled and tested. The GitHub workflow will build both after you push the project.

This is an independent portfolio project, not a Salesforce product or a proven fix in a customer's org. There is no LLM call, PostgreSQL persistence, live Salesforce collector, Playwright collector, or OpenTelemetry export yet. Deliberately avoid claiming those on a resume until implemented and measured.

## Run now (Java 21 JDK)

macOS/Linux/Git Bash:

```sh
./scripts/test.sh
./scripts/run.sh
```

Open http://localhost:8080. Run the broken configuration, inspect a failed check, then run the corrected configuration. Export either report.

Windows PowerShell, from the project folder:

```powershell
New-Item -ItemType Directory -Force build/classes
javac -d build/classes (Get-ChildItem src/main/java/dev/handoffguard/*.java).FullName (Get-ChildItem src/test/java/dev/handoffguard/*.java).FullName
java -cp build/classes dev.handoffguard.EngineTest
java -cp build/classes dev.handoffguard.Server
```

Optional Spring adapter (Java 21 + Maven 3.6.3+ and network access):

```sh
mvn -f spring/pom.xml spring-boot:run
```

Stop the local server first; both use port 8080. The dependency-free server binds to loopback; the Spring adapter is an unauthenticated development service and is not ready for public deployment.

## What it checks

1. Explicit human request results in receiving-side acceptance.
2. Two order-service failures trigger escalation without unbounded retries.
3. No queue capacity creates exactly one follow-up case and an honest status message.
4. Order ID, issue and attempted steps survive the handoff.
5. Duplicate retries create one acceptance, using an idempotency key in the simulator.
6. Order support reaches the Retail Support queue.

Checks also enforce session correlation, a 5-second simulated handoff SLO and acceptance after the transfer request. Current context checks validate presence, not semantic correctness. The time measurements are synthetic fixture timestamps, not load-test results or real Salesforce performance.

## Design

`Engine.verify()` is independent of `RetailSimulator`. It accepts normalized events, filters by session, orders by event time and applies deterministic assertions. Removing an acceptance event cannot be concealed by a customer-facing message. The simulator implements routing, unavailable queues, context projection and idempotent acceptance; broken mode injects faults in these behaviors.

- `src/main/java/dev/handoffguard/Engine.java`: domain records, assertion engine and fake transport.
- `Server.java`: dependency-free local REST/static server.
- `EvidenceCli.java`: offline evidence import.
- `web/`: framework-free, responsive dashboard; renders API responses using textContent.
- `spring/`: optional Spring Boot HTTP adapter over the same engine.
- `src/test/`: regression and mutation cases, including missing evidence and cross-session evidence.
- `.github/workflows/ci.yml`: regression reports and Spring packaging.

Runs are not persisted by the server. Export JSON to retain a run. All order/customer examples are synthetic.

## API

`POST /api/run?profile=broken` or `profile=fixed` returns a run, checks and events.
`GET /api/health` reports `salesforceConnected: false`.
Invalid profiles return HTTP 400. The local server rejects GET requests to the run endpoint.

## Import normalized evidence

Compile using `scripts/test.sh`, then:

```sh
java -cp build/classes dev.handoffguard.EvidenceCli CONTEXT example-session docs/example-evidence.tsv
```

Input TSV columns: `session`, `atMs`, `type`, `key`, `value`. One attribute per row; rows sharing session/time/type form one event. Do not use literal tabs/newlines inside values. The collector must ensure distinct same-type events have distinct timestamps; this v0 format cannot distinguish identical timestamp/type events. A failed assertion exits 1, successful verification exits 0; invalid input throws a nonzero error. Imported evidence is labeled `IMPORTED_UNVERIFIED`, never automatically certified as live Salesforce evidence.

## Why Salesforce

Salesforce's Agentforce DX documentation says preview does not support escalation and instructs developers to test the published agent through the desired endpoint. This project targets that boundary: assertions about what the receiving side actually observes.

- https://developer.salesforce.com/docs/ai/agentforce/guide/agent-dx-nga-preview.html
- https://help.salesforce.com/s/articleView?id=ai.agent_testing_center_considerations.htm&language=en_US&type=5
- https://architect.salesforce.com/docs/architect/well-architected/guide/agentic-enterprise-reliability.html

Documentation reviewed November 13, 2026; verify current behavior for the target org/release. This project complements existing testing tools; it does not establish that Salesforce lacks all end-to-end testing capability.

See `docs/SALESFORCE_INTEGRATION.md` for the next milestone and `docs/DEVELOPMENT_LOG.md` for validation and review notes.
