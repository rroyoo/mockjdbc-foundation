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
- [ ] `open` E2E test: proxy capture -> Kafka -> WireMock bridge -> WireMock gRPC -> mock driver, with the stub generated from the captured event instead of hand-written.
  - Exit criteria: a query executed against H2 through the proxy is replayed by the mock driver with identical rows and parameters, with no hand-written stub.

### M2. Procedures beyond IN parameters
- [x] `done` OUT / INOUT parameters in record and mock. The wiremock mapper emits `callableStatement.parameters` (OUT/INOUT) in the stub response; the mock exposes them via index-based `getXxx(int)`.
  - Limits: name-based OUT getters still return defaults; INOUT is lossy (the captured value is the OUT value); OUT values are not part of the stub key. H2 functions called with `{? = call f()}` return a result set from `execute()`, which the proxy does not defer, so the sample uses `executeUpdate()`.
- [ ] `open` Procedure returning several rows and returning none.
- [ ] `open` Procedure error surfaces as `SQLException` (type and message) in proxy and mock.

### M3. Writes and transactions
- [ ] `open` `executeUpdate` with generated keys.
- [ ] `open` Batch (`addBatch` / `executeBatch`).
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
- [ ] `open` Confirm Kafka Testcontainers tests run (not skipped) in CI.
- [ ] `open` JaCoCo report without threshold.
- [ ] `open` Protobuf compatibility check for `mockjdbc-proto`.

## Next recommended slice

M2 remainder (procedure returning several rows / none, procedure error as `SQLException`), then M1 (E2E record→Kafka→WireMock→mock).

## Log

- 2026-10-12: Roadmap created. Baseline delivered in PR #11 (procedure IN parameters, Testcontainers-managed mock backend).
- 2026-10-12: M2 OUT/INOUT done (mock handler, `PreparedStatementQueryHandler.onOutParameters`, `MockedQueryStubMapper` OUT response, sample record+mock tests, stub `find-mock-call-add-scalar-out.json`).
