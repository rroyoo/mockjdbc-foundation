# Test Validation Roadmap

Canonical checklist for the integration-test work that validates the `mockjdbc-mock` and `mockjdbc-proxy` libraries through `mockjdbc-spring-users` (and the reactor integration tests). Update it in the same change as each slice so work can resume from here.

Related: `docs/next-steps.md` (general backlog), `docs/jdbc-supported-surface.md`, `docs/module-status-matrix.md`.

Statuses: `done`, `partial`, `open`, `deferred`, `blocked`.

## How to run the integration tests

```bash
cd mockjdbc && mvn -B -ntp install -DskipTests   # once, or after reactor changes
cd ../mockjdbc-spring-users && mvn -B -ntp test  # Docker + Compose v2 required
mvn -Dtest=UserControllerH2ProfileTest test        # record flow (H2 + proxy capture), no Docker
mvn -Dtest=UserControllerMockJdbcProfileTest test  # mock flow (WireMock stack via Testcontainers)
```

Conventions: JUnit 5, `@DisplayName` in Given-When-Then, `should...` method names. The `mockjdbc` profile tests start `docker-compose.yml` through Testcontainers `ComposeContainer` with `withLocalCompose(true)` (the stack bind-mounts sibling paths). WireMock stubs live in `mockjdbc-spring-users/docker/wiremock/mappings/`.

## Baseline (done)

- [x] Sample suite: H2 record flow and mock flow, 6 tests, Docker-managed backend (PR #11).
- [x] Procedure call with IN parameters in both flows (`{ call ADD_NUMBERS(?, ?) }`, 19 + 23 = 42).
- [x] Record flow asserts the captured `CallableStatement` event (SQL and bound parameters).

## Milestones

### M1. Record -> mock round trip
- [x] `done` E2E test: proxy capture -> Kafka -> WireMock bridge -> WireMock gRPC -> mock driver, with the stub generated from the captured event instead of hand-written.
  - Exit criteria: a query executed against H2 through the proxy is replayed by the mock driver with identical rows and parameters, with no hand-written stub.
  - `RecordReplayEndToEndTest` verifies the generated mapping and replays the captured H2 result through the mock driver. Mock query SQL is normalized with the same trim/lowercase/whitespace/trailing-semicolon rules as the bridge so captured mappings match consistently.

### M2. Procedures beyond IN parameters (`done`)
- [x] `done` OUT / INOUT parameters in record and mock. The wiremock mapper emits `callableStatement.parameters` (OUT/INOUT) in the stub response; the mock exposes them via index-based `getXxx(int)`.
  - Limits: name-based OUT getters still return defaults; INOUT is lossy (the captured value is the OUT value); OUT values are not part of the stub key. H2 functions called with `{? = call f()}` return a result set from `execute()`, which the proxy does not defer, so the sample uses `executeUpdate()`.
- [x] `done` Procedure returning several rows and returning none in record/H2 and mock flows; captured result row counts are asserted.
- [x] `done` Procedure error surfaces as `SQLException` in record/H2 and mock; the captured event's exception type/message and the replayed exception type/message are asserted.
  - Exit criteria: OUT/INOUT values, multiple/empty results, and SQL exception type/message are covered in both record and replay flows.

### M3. Writes and transactions
- [x] `done` `executeUpdate` with generated keys. The H2 capture → Kafka → WireMock → mock-driver round trip preserves the update count and generated-key rows.
- [x] `done` Batch (`Statement` and `PreparedStatement`, including `executeLargeBatch`). Capture/replay preserves per-command indices, update counts, JDBC `SUCCESS_NO_INFO` values, and `BatchUpdateException` partial counts, including repeated identical commands with different outcomes.
  - Exit criteria: an H2 prepared batch containing duplicate deletes replays counts `[1, 0]` through Kafka/WireMock; unit coverage verifies plain/prepared batches, large counts, count sentinels, and partial failures.
- [ ] `open` `commit` / `rollback` / `autoCommit`.
- [ ] `open` `GET /users/{id}`, `PUT /users/{id}`, `DELETE /users/{id}` covered in both flows.

### M4. Type and metadata fidelity
- [ ] `open` Nulls, `DECIMAL`, dates/time zones, `BLOB` / `CLOB`.
- [ ] `blocked` `ResultSetMetaData` precision, scale, nullability. Depends on `docs/next-steps.md` backlog item 1 (`ColumnMetadata` extension).

### M5. Proxy behaviour
- [ ] `open` Kafka failure or full queue does not break the application; drop policy and statistics asserted.
- [ ] `open` Distinct `datasourceId` values and datasource-oriented Kafka keys.
- [ ] `deferred` Masking of sensitive parameters (feature not implemented yet).

### M6. Mock robustness
- [ ] `open` No matching stub (default empty result) behaviour asserted.
- [ ] `open` Backend down / timeout surfaces a clear `SQLException`.
- [ ] `open` Concurrent connections and statements.

### M7. CI quality
- [x] `done` Confirm both Kafka Testcontainers suites run without skips in GitHub Actions (PR #14).
- [x] `done` JaCoCo line-coverage report with a combined minimum of 50%; 90% remains the target.
- [ ] `open` Protobuf compatibility check for `mockjdbc-proto`.

## Next recommended slice

M3 transaction behavior (`commit` / `rollback` / `autoCommit`), then the remaining user API writes.

## Log

- 2026-10-12: Roadmap created. Baseline delivered in PR #11 (procedure IN parameters, Testcontainers-managed mock backend).
- 2026-10-12: M2 OUT/INOUT done (mock handler, `PreparedStatementQueryHandler.onOutParameters`, `MockedQueryStubMapper` OUT response, sample record+mock tests, stub `find-mock-call-add-scalar-out.json`).
- 2026-10-12: M2 closed (record tests assert multiple/empty rows and captured SQLExceptions; mock tests replay multiple/empty rows and reconstruct the SQLNonTransientException). The focused sample tests passed: 12 run, 0 failures/errors/skips.
- 2026-11-04: M1 done. Added an E2E H2 capture → Kafka → WireMock bridge → gRPC → mock-driver test that replays a generated stub without a hand-written mapping. The test exposed and fixed SQL normalization mismatch between bridge matchers and mock-driver lookup requests.
- 2026-11-04: CI quality gate implemented in ADR-0005: GitHub Actions builds both Maven projects, reports test/coverage totals, enforces 50% combined line coverage, and publishes CodeQL findings. Local verification passed 349 tests with no skips and 75.45% line coverage. Testcontainers was updated to 1.21.4 with Docker API 1.44 pinned so the Kafka integration suites run on Docker Engine 29; the CI summary fails if either is skipped. PR #14 passed both push- and PR-triggered workflows, including CodeQL and the required non-skipped Kafka checks.
- 2026-11-05: M3 generated keys done. Added an additive `MockedQuery.generated_keys` field and verified that H2-captured `PreparedStatement.executeUpdate` returns the same update count and generated key through the WireMock replay path. The next slice is prepared batch execution.
- 2026-11-07: M3 batch execution done. Added batch indices to the event and lookup contracts so identical entries can map to distinct outcomes; plain/prepared capture and replay preserve counts, including large counts and JDBC sentinels. An H2 duplicate-delete batch round-trips `[1, 0]` through Kafka → WireMock → mock driver.
- 2026-11-07: Batch review follow-up preserves partial `BatchUpdateException` counts during capture and recreates `BatchUpdateException` during replay; regression tests cover plain and large prepared batches plus replayed partial counts.
