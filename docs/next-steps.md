# Next Steps

Hand-off for continuing project improvements. Last updated: 2026-11-27.

Read first: `docs/module-status-matrix.md`, `docs/jdbc-supported-surface.md`, `.github/copilot-instructions.md`.

## Public open-source launch

The user-facing documentation (Docusaurus), community onboarding, and Maven beta publication plan is tracked separately in [`open-source-launch-roadmap.md`](open-source-launch-roadmap.md). This launch roadmap is prioritized independently; the JDBC implementation backlog below remains paused.

## Current baseline

- Reactor (`mockjdbc/`) and sample (`mockjdbc-spring-users/`) build green on Java 21 in CI.
- Last recorded test counts (2026-11-22): proxy 68 (1 skipped), mock 232, wiremock 22 (1 skipped). The skipped tests are the Testcontainers/Kafka integration tests.
- `Connection` gaps are closed (validity/abort, wrappers, metadata, type map, LOB/array/struct factories). `createSQLXML` and request/sharding methods are explicitly unsupported.
- `ResultSetFactory` coerces values to the declared SQL type; `DriverManager` discovers `MockDriver` via `META-INF/services/java.sql.Driver` and static self-registration.
- The sample E2E test now proves H2 capture → Kafka → WireMock bridge → gRPC → mock-driver replay without a hand-authored mapping; lookup SQL normalization is aligned with bridge matching.
- The same end-to-end path now captures and replays `PreparedStatement.executeUpdate` generated keys separately from the affected-row count.
- Statement and prepared batches now preserve entry indices and per-entry update counts through H2 capture → Kafka → WireMock → mock driver, including repeated identical commands with different results.

## Local build

```bash
# Maven >= 3.9.6 is required (3.8.x fails on the protobuf plugin)
cd mockjdbc && mvn -B -ntp install            # reactor; install is required by the sample
cd ../mockjdbc-spring-users && mvn -B -ntp test
```

Notes from CI debugging:
- Mockito inline mock maker could not self-attach on the GitHub runner. Surefire in `mockjdbc-proxy` and `mockjdbc-wiremock` starts the JVM with `-javaagent:<byte-buddy-agent.jar>`; keep that wiring.
- The parent POM uses `${revision}` with `flatten-maven-plugin`, so installed POMs resolve. `.flattened-pom.xml` is git-ignored.
- Docker is needed for Testcontainers tests; they are skipped locally when Docker is not usable for them.

## Project completion roadmap

This is the canonical repository-level delivery plan. `docs/test-validation-roadmap.md` remains the evidence log for the completed M1-M7 integration-validation work.

Completion means the supported debugging/testing workflow is implemented and tested, unsupported behavior is explicit, the record/replay path remains verified in CI, relevant data-handling risks are documented or deliberately excluded, and module/ADR documentation matches the code. It does not imply full JDBC API parity or production deployment readiness.

### Phase 0 — Record/replay foundation and validation (`done`)

- [x] Capture and replay JDBC work through proxy -> Kafka -> WireMock bridge -> gRPC -> mock driver, including generated keys, batches, metadata, temporal values, LOBs, and callable results.
- [x] Verify backend errors, concurrent statements, and Docker-backed Kafka integration tests in CI.
- [x] Enforce the 50% combined coverage floor, CodeQL, test summaries, and Buf `FILE` compatibility against `main` (merged in PR #36).

**Exit criteria:** M1-M7 are complete in `docs/test-validation-roadmap.md`; required end-to-end CI checks pass.

### Phase 1 — Close the mock JDBC statement contract (`open`, paused during public launch)

- [ ] Implement or explicitly reject `unwrap` / `isWrapperFor` for `Statement`, `PreparedStatement`, and `CallableStatement`.
- [ ] Implement or explicitly reject modern convenience methods (`enquoteLiteral`, `enquoteIdentifier`, `isSimpleIdentifier`, `enquoteNCharLiteral`).
- [ ] Decide and enforce result-set type, concurrency, and holdability semantics for statement-creation overloads.
- [ ] Inventory remaining prepared-only and callable methods; resolve name-based OUT getters that currently return defaults, INOUT capture limits, and advanced callable types.
- [ ] Apply the SQL exception policy consistently; do not silently return defaults for unsupported methods.
- [ ] Add focused tests and update `docs/jdbc-supported-surface.md` for each method group.

**Exit criteria:** every in-scope statement-family method is behavior-tested, or fails explicitly with the documented JDBC exception; overload behavior and callable limitations are reflected in the support matrix.

### Phase 2 — Complete result and metadata fidelity (`open`)

- [ ] Define and test handling for pass-through wire kinds: `CHAR`/`VARCHAR` variants, `ARRAY`, `STRUCT`, `SQLXML`, `NULL`, and `OTHER` (coerce supported values or reject unsupported ones explicitly).
- [ ] Keep proxy capture, Protobuf, WireMock mapping, and mock-driver conversion aligned for every supported value type.
- [ ] Make an explicit support decision for `DatabaseMetaData` catalog queries; they remain unsupported unless a concrete use case is accepted.

**Exit criteria:** each value type in scope round-trips with deterministic type/null behavior; incompatible or unsupported values fail clearly, with unit and end-to-end coverage where applicable.

### Phase 3 — Data handling and bridge resilience (`deferred`)

- [ ] Decide whether masking of captured values is needed for debugging workflows; captured parameters and results are currently unmasked and sent to Kafka.
- [ ] Consider bounded retries or a dead-letter path for WireMock registration failures if debugging use cases require recovery beyond the current behavior.
- [ ] If either behavior is adopted, add focused tests for the chosen policy and its effect on later records.

**Exit criteria:** document and test any adopted data-handling or recovery policy. These are not production-readiness or public-beta launch gates for this debugging/testing tool.

### Phase 4 — Build, test, and release readiness (`open`)

- [ ] Raise combined line coverage from the 50% gate toward the 90% target; adopt staged thresholds based on measured coverage.
- [ ] Pin Maven plugin versions that currently produce model warnings (`maven-jar-plugin` in `mockjdbc-proxy`, `maven-surefire-plugin` in proxy and WireMock); decide whether a Maven Wrapper is needed.
- [ ] Keep the standalone sample's clean bootstrap documented (`mvn install` for the reactor first); Docker-backed `mockjdbc` profile tests must continue running without skips in CI and may be unavailable locally without Docker.
- [x] Define the version/tag and package-publication policy and configure tag-triggered publishing. The first beta and clean-consumer validation are tracked in `docs/open-source-launch-roadmap.md`.

**Exit criteria:** a clean environment can build the reactor and sample using documented commands; coverage reaches 90% combined or the target is explicitly revised by ADR; required integrations pass in CI; release policy and publishing workflow are documented.

### Phase 5 — Documentation and governance closeout (`open`)

- [x] Reconcile historical proxy-placeholder wording and checklists in ADR-0001 with the implemented active/partial modules and this roadmap.
- [x] Review ADR-0002 / ADR-0004 status labels against their completed acceptance checklists; retain `Accepted` with the implementation status recorded explicitly.
- [ ] Reconcile ADR-0003's open exception-policy and supported-surface checks with current implementation and tests.
- [x] Keep Qodana out of scope; current quality gates use GitHub Actions, CodeQL, Buf, and JaCoCo.
- [x] Keep the module-status matrix and validation roadmap aligned with these phase statuses.

**Exit criteria:** status labels, acceptance checklists, and next milestones agree across `docs/next-steps.md`, ADRs, `docs/module-status-matrix.md`, and `docs/test-validation-roadmap.md`.

## Deferred or explicitly unsupported

- **Transaction effects:** `commit` / `rollback` remain no-ops because replay is stateless; revisit only if mutable transactional replay becomes a requirement.
- **Connection APIs:** `createSQLXML`, request/sharding methods, and unwired JDBC methods remain explicitly unsupported unless a concrete use case changes scope.
- **Catalog metadata:** `DatabaseMetaData` catalog queries remain unsupported pending a demonstrated need.
- **Maven Central release:** versioning policy and tag-triggered publishing are configured; the first release and clean-consumer validation remain pending. See `docs/open-source-launch-roadmap.md`.

## Sequencing and dependencies

- The JDBC implementation backlog remains paused while the public beta launch is prioritized. Phase 1 is the first implementation slice when that backlog resumes.
- Phase 2 follows the JDBC contract decisions; any Protobuf change must pass the Buf compatibility gate, and intentional breaking changes need an explicit versioning decision.
- Phase 3 is deferred and is not a production-readiness gate for the debugging/testing scope.
- Phase 4 quality work can proceed independently; first-beta publication and clean-consumer validation are tracked in the public-launch roadmap.

## Working agreements

- Keep module boundaries: proto owns contracts, proxy owns capture/publication, mock owns replay/JDBC behavior, wiremock owns Kafka-to-stub registration.
- Unsupported JDBC operations throw `SQLFeatureNotSupportedException`; no silent fallbacks.
- One focused handler per JDBC feature group in `mockjdbc-mock`; handler state is per connection, never static.
- Tests: JUnit 5, behavior-focused `@DisplayName`, parameterized tests for matrices.
- Update docs (supported surface, module matrix, ADR checklists) in the same change as behavior.
