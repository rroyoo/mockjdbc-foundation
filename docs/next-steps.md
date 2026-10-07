# Next Steps

Hand-off for continuing project improvements. Last updated: 2026-10-07, branch `feat/jdbc-gaps` (based on `main` after PR #9).

Read first: `docs/module-status-matrix.md`, `docs/jdbc-supported-surface.md`, `.github/copilot-instructions.md`.

## Current baseline

- Reactor (`mockjdbc/`) and sample (`mockjdbc-spring-users/`) build green on Java 21 in CI.
- Test counts: proxy 68 (1 skipped), mock 232, wiremock 22 (1 skipped). The skipped tests are the Testcontainers/Kafka integration tests.
- `Connection` gaps are closed (validity/abort, wrappers, metadata, type map, LOB/array/struct factories). `createSQLXML` and request/sharding methods are explicitly unsupported.
- `ResultSetFactory` coerces values to the declared SQL type; `DriverManager` discovers `MockDriver` via `META-INF/services/java.sql.Driver` and static self-registration.

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

## Backlog (priority order)

### 1. Decide: extend `ColumnMetadata` (cross-module contract)
Precision, scale, nullability, table/schema/catalog names and auto-increment do not travel in `ColumnMetadata`, so replayed `ResultSetMetaData` reports nullability as unknown and zero precision/scale.
- Change must touch together: `mockjdbc-proto` schema (additive fields only), proxy capture (`ByteBuddyResultSetWrapperFactory.toColumnMetadata`), WireMock mapper JSON (`MockedQueryStubMapper.buildSuccessJsonBody`), `ResultSetFactory.configureColumn`, and tests in all three.
- Exit criteria: round trip proxy -> stub -> mock preserves the new fields; old events without them still replay.

### 2. Statement family gaps (ADR-0001 section E, ADR-0003)
- [ ] `unwrap` / `isWrapperFor` on `Statement`, `PreparedStatement`, `CallableStatement`.
- [ ] Modern convenience methods (`enquoteLiteral`, `enquoteIdentifier`, `isSimpleIdentifier`, `enquoteNCharLiteral`).
- [ ] Decide and enforce semantics for result-set type / concurrency / holdability overloads (currently accepted but not enforced).
- [ ] Remaining prepared-only and callable methods that are not intercepted.
- Update `docs/jdbc-supported-surface.md` and add targeted tests per group.

### 3. `ResultSetFactory` follow-ups
- [ ] Types still passed through by wire kind: `CHAR`/`VARCHAR` variants, `BLOB`/`CLOB`, `ARRAY`, `STRUCT`, `SQLXML`, `NULL`, `OTHER`. Decide per type: coerce, or reject with `SQLFeatureNotSupportedException`.
- [ ] Time zone semantics for `DATE`/`TIME` derived from protobuf timestamps (currently JVM default zone).
- [ ] `DatabaseMetaData` catalog queries are unsupported; revisit only if a real use case appears.

### 4. CI and quality
- [ ] Confirm Testcontainers tests really run in CI (check the CI log for "Tests run" with 0 skipped for `KafkaMappingConsumerIntegrationTest` and `KafkaMockedQueryEventProducerIntegrationTest`).
- [ ] End-to-end test: proxy -> Kafka -> WireMock bridge -> WireMock gRPC -> mock driver.
- [ ] Add JaCoCo report (no threshold at first).
- [ ] Pin `maven-jar-plugin` version in `mockjdbc-proxy/pom.xml` (Maven warns it is missing) and consider a Maven wrapper (`.mvn/wrapper`).
- [ ] Qodana: `qodana.yaml` and `.github/workflows/qodana_code_quality.yml` are untracked. Either add them and configure the `QODANA_TOKEN` secret, or delete them.

### 5. Security and robustness
- [ ] Proxy: option to mask sensitive parameters/columns before publishing to Kafka.
- [ ] WireMock bridge: bounded retries and a dead-letter path for records whose registration keeps failing (today a failing record is retried indefinitely on the poll thread).
- [ ] Protobuf compatibility check in CI (for example `buf breaking`) for `mockjdbc-proto`.

### 6. Documentation upkeep
- [ ] ADR-0001 still has unchecked items that may already be done (for example reclassifying `mockjdbc-proxy` as active); verify against code and tick or update.
- [ ] ADR-0002 / ADR-0004 checklists: re-verify the Docker-backed integration items once CI evidence exists.
- [ ] `mockjdbc-spring-users` status in the module matrix is `partial`; the `mockjdbc` profile tests are skipped by default.

## Working agreements

- Keep module boundaries: proto owns contracts, proxy owns capture/publication, mock owns replay/JDBC behavior, wiremock owns Kafka-to-stub registration.
- Unsupported JDBC operations throw `SQLFeatureNotSupportedException`; no silent fallbacks.
- One focused handler per JDBC feature group in `mockjdbc-mock`; handler state is per connection, never static.
- Tests: JUnit 5, behavior-focused `@DisplayName`, parameterized tests for matrices.
- Update docs (supported surface, module matrix, ADR checklists) in the same change as behavior.
