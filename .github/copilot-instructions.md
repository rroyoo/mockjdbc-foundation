# Copilot instructions for MockJDBC

## Build, test, and lint

The repository contains two Maven builds:

- `mockjdbc/` is the multi-module reactor for the reusable libraries.
- `mockjdbc-spring-users/` is an independent Spring Boot sample and is not a reactor module.

The reactor targets Java 17 and requires Maven 3.9.6 or newer because the configured Protobuf plugin enforces that minimum. CI runs on Java 21 and requires Docker because some integration tests use Testcontainers.

```bash
# Build and test all reusable modules
cd mockjdbc
mvn clean verify

# Run all tests without cleaning
mvn test

# Build/test one module and its reactor dependencies
mvn -pl mockjdbc-mock -am test
mvn -pl mockjdbc-proxy -am test
mvn -pl mockjdbc-wiremock -am test

# Run one test class (from mockjdbc/)
mvn -pl mockjdbc-mock -am -Dtest=ConnectionFactoryTest test

# Run one test method
mvn -pl mockjdbc-mock -am -Dtest=ConnectionFactoryTest#shouldCreateConnectionFromMockConfig test

# Test the standalone sample
cd ../mockjdbc-spring-users
mvn test
```

For a clean sample-app build, build the sibling reactor first because the sample currently consumes local `target/*.jar` files through `systemPath` dependencies:

```bash
cd mockjdbc
mvn clean package -DskipTests
cd ../mockjdbc-spring-users
mvn test
```

There is no separate lint or formatting Maven task configured. Use the CI-equivalent commands when checking a change: `cd mockjdbc && mvn -B -ntp verify`, followed by `cd ../mockjdbc-spring-users && mvn -B -ntp test`.

## Architecture

MockJDBC is a record/replay pipeline split across reusable Maven modules:

1. `mockjdbc-proto` owns the Protobuf and gRPC contracts. Generated classes are consumed by the capture, mock, and WireMock modules; change schemas only with all consumers in mind.
2. `mockjdbc-proxy` is a ByteBuddy Java agent. It instruments `DataSource.getConnection()`, wraps returned JDBC connections/statements with JDK proxies, captures SQL, parameters, results, timing, and status, then dispatches `MockedQuery` events locally and to producers such as Kafka.
3. `mockjdbc-wiremock` consumes those Kafka `MockedQuery` events, normalizes SQL, maps events to deterministic WireMock stubs, and registers/upserts them. The bridge is lifecycle-managed through `WireMockKafkaBridgeExtension`.
4. `mockjdbc-mock` is the replay-side JDBC driver. `MockDriver` parses `jdbc:mock://` URLs into `MockConfig`; `ConnectionFactory` and `StatementFactory` create runtime JDBC implementations with ByteBuddy; statement handlers call `GrpcMockQueryClient`; `ResultSetFactory` converts the gRPC payload back into JDBC results.
5. `mockjdbc-spring-users` demonstrates H2, mock-driver, and proxy-agent profiles. It is an integration sample, not part of the `mockjdbc` reactor, and its local system-scoped JAR dependencies mean the reusable reactor must be packaged first.

The WireMock module has two runtime integration points: `WireMockKafkaBridgeExtension` owns the server extension lifecycle, while the `META-INF/services/com.github.tomakehurst.wiremock.extension.ExtensionFactory` entry enables WireMock discovery. Its shaded artifact is intended for the WireMock extensions directory and deliberately excludes gRPC transport dependencies supplied by the WireMock gRPC runtime.

The intended end-to-end flow is:

```text
real DataSource
  -> mockjdbc-proxy agent
  -> MockedQuery protobuf
  -> Kafka
  -> mockjdbc-wiremock bridge
  -> WireMock gRPC stub
  -> mockjdbc-mock driver
  -> application JDBC result
```

Read `docs/module-status-matrix.md` and `docs/jdbc-supported-surface.md` before extending a module. Current JDBC support is intentionally partial, especially for wrapper, metadata, newer convenience, and advanced callable methods.

## Repository-specific conventions

- Keep dependency versions in the parent `mockjdbc/pom.xml`; child reactor POMs declare dependencies without versions whenever dependency management covers them. Keep the sample app's separately managed versions consistent with the reactor.
- Keep module boundaries intact: proto owns contracts, proxy owns capture/publication, mock owns replay/JDBC behavior, and wiremock owns Kafka-to-stub registration. Do not move transport or integration concerns into JDBC handlers.
- Keep Protobuf changes in `mockjdbc-proto` and verify generated consumers through the reactor build. Do not hand-edit generated sources or `target/` output.
- In `mockjdbc-mock`, use one focused handler per JDBC feature group. Handler state must be per generated JDBC instance, not static. Wire each handler explicitly in the relevant ByteBuddy factory; use narrow `named(...)`/argument matchers and avoid catch-all delegation that can bind `Object` methods or create ambiguity.
- Preserve the JDBC error contract: backend and mapping failures surface as clear `SQLException` paths. Do not add silent fallbacks for unsupported operations.
- Keep query transport isolated in `GrpcMockQueryClient` and JDBC semantics in statement/connection handlers. Keep result conversion deterministic, including nulls, metadata, update counts, generated keys, and batch behavior.
- For the proxy, require explicit datasource identity at registration. Kafka event keys are datasource-oriented by default, and async dispatch behavior is controlled through `AsyncDispatchConfig`; preserve bounded queue/drop/failure statistics.
- For the WireMock bridge, keep consumer, mapper, and registrar responsibilities separate. Lifecycle must be explicit (`start`/`close`), mapping must be deterministic and idempotent, and SQL normalization is trim, lowercase, whitespace collapse, and trailing-semicolon removal. Preserve datasource/SQL filters before mapping.
- Preserve the WireMock packaging contract: keep the extension service descriptor registered, use the provided WireMock dependency for compilation, and keep shaded runtime contents compatible with the external WireMock gRPC extension.
- Tests use JUnit 5 and behavior-focused `@DisplayName` names. Use parameterized tests for scenario matrices. Kafka/Testcontainers integration tests need Docker and may be skipped when Docker is unavailable.
- When changing a Protobuf field or event mapping, update the producer, bridge mapper, mock-side client/result conversion, and relevant tests together; the event is the cross-module compatibility boundary.
- Update the related README, ADR, module status, or JDBC support matrix when behavior or module scope changes. ADR checklists and status docs are maintained as implementation evidence, not aspirational plans.
- Existing focused agent guidance lives in `.github/agents/`; reusable implementation guidance lives in `.github/skills/`. Use those files when a task matches their scope, especially for ByteBuddy delegation, JDBC state semantics, Kafka lifecycle, and WireMock mapping.
