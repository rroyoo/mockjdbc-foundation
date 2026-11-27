# MockJDBC Technical Architecture

**Technical documentation for developers and integrators.**

[![CI](https://github.com/rroyoo/mockjdbc-foundation/actions/workflows/ci-build-and-test.yml/badge.svg?branch=main)](https://github.com/rroyoo/mockjdbc-foundation/actions/workflows/ci-build-and-test.yml)

For project stewardship context and backlog status, see [`docs/adr/ADR-0001-project-stewardship-and-gap-closure.md`](../docs/adr/ADR-0001-project-stewardship-and-gap-closure.md).

---

## Overview

MockJDBC is a multi-module Maven project with a partially supported mock JDBC driver backed by gRPC for query resolution.

---

## Modules

### `mockjdbc-proxy`
**Status: partial/active Java-agent capture module**

The module is included in the reactor and instruments JDBC `DataSource` implementations with a Java agent. Registered data sources return capturing connection proxies that publish SQL and ResultSet events.

- Current state: agent, JDBC wrappers, ResultSet capture, and Kafka event publishing are implemented
- Intended scope: transparent SQL and ResultSet capture without DataSource wrapping
- Limitation: applications must start with `-javaagent` or install the agent programmatically

**Dependencies:**
- ByteBuddy - agent instrumentation and advice
- Kafka clients - event publishing

### `mockjdbc-proto`
**gRPC service contracts and Protobuf definitions**

Defines the protocol between MockJDBC and the remote query mock service.

- `src/main/protobuf/` - Protobuf `.proto` files defining `MockQueryService`
- Generated stubs - `MockQueryServiceGrpc`, `QueryLookupRequest`, `MockedQuery`, result-set/statement messages
- Protocol: `FindMock(QueryLookupRequest) -> MockedQuery`

**Key files:**
- `src/main/protobuf/io/github/rroyoo/mockjdbc/mock/mocked_query_service.proto`
- Related JDBC message schemas in `src/main/protobuf/io/github/rroyoo/mockjdbc/mock/`

### `mockjdbc-mock`
**JDBC driver implementation with ByteBuddy-generated JDBC interfaces and gRPC query lookup**

Implements the JDBC `Driver` and dynamically generates `Connection`/statement-family implementations.

**Key classes:**

| Class | Responsibility |
|---|---|
| `MockDriver` | JDBC `Driver` implementation; accepts `jdbc:mock://` URLs |
| `ConnectionFactory` | Builds runtime `Connection` implementations via ByteBuddy |
| `LifecycleHandler`, `TransactionHandler`, `ConfigHandler`, `WarningsHandler`, `ClientInfoHandler` | Connection state/behavior delegates used by `ConnectionFactory` |
| `StatementFactory` | Builds runtime `Statement` / `PreparedStatement` / `CallableStatement` implementations via ByteBuddy |
| `StatementQueryHandler`, `PreparedStatementQueryHandler`, `CallableStatementOutParamHandler`, and companion handlers | Statement execution, parameter binding, lifecycle/config/warnings, and callable OUT parameter behavior |
| `GrpcMockQueryClient` + `ResultSetFactory` | gRPC lookup (`MockQueryService.FindMock`) and materialization of JDBC `ResultSet` |

**Flow:**
1. Application calls `DriverManager.getConnection("jdbc:mock://...")`
2. `MockDriver.connect()` parses URL into `MockConfig`
3. `ConnectionFactory.create()` returns a ByteBuddy-generated `Connection`
4. Connection statement methods delegate to `StatementFactory`
5. Statement handlers call `GrpcMockQueryClient`, then `ResultSetFactory` maps gRPC payloads to JDBC `ResultSet`

---

## Build Structure

### Parent POM (`pom.xml`)

Dependency and plugin versions are managed centrally in the parent POM:

```xml
<dependencyManagement>
  <dependencies>
    <!-- All versions defined here -->
    <dependency>
      <groupId>io.grpc</groupId>
      <artifactId>grpc-bom</artifactId>
      <version>1.79.0</version>
      <type>pom</type>
      <scope>import</scope>
    </dependency>
    <!-- ... more dependencies ... -->
  </dependencies>
</dependencyManagement>

<pluginManagement>
  <plugins>
    <!-- All plugin versions defined here -->
  </plugins>
</pluginManagement>
```

**Key properties (alphabetically sorted):**

| Property | Value | Usage |
|---|---|---|
| `java.version` | `17` | Java compilation target |
| `maven.version.grpc` | `1.79.0` | gRPC BOM version |
| `maven.version.protobuf` | `4.34.0` | Protobuf compiler/library |
| `maven.version.mockito` | `5.12.0` | Mocking framework (tests) |
| `maven.version.test.junit-jupiter` | `5.11.3` | JUnit 5 (tests) |

### Child POMs

Each child module **declares dependencies without versions**; versions are inherited from parent `<dependencyManagement>`.

**Example (mockjdbc-proto):**
```xml
<dependency>
  <groupId>io.grpc</groupId>
  <artifactId>grpc-stub</artifactId>
  <!-- Version inherited from parent -->
</dependency>
```

---

## Build & Test

### Compile

```bash
cd mockjdbc
mvn clean compile
```

Compiles all modules and generates gRPC stubs from Protobuf definitions.

### Test

```bash
mvn test
```

Runs all unit tests across modules. All tests follow JUnit5 conventions:
- `@DisplayName` on every test method
- `@ParameterizedTest` for multiple scenarios
- No unused imports, variables, or commented code

Static checks run during `validate` on every Maven build: Spotless checks Java formatting
with Google Java Format (AOSP style), and PMD fails on cyclomatic complexity of 10 or more
in production and test methods/constructors. The formatter requires Java 21 or newer to run;
compiled MockJDBC artifacts remain Java 17 compatible. To apply formatting before rebuilding:

```bash
mvn spotless:apply
```

### Full Verification

```bash
mvn clean verify
```

Compiles and tests the reactor modules, and runs the configured Maven validation checks.

---

## Quality Gates

- Maven `validate` runs Spotless (Google Java Format, AOSP style) and PMD (cyclomatic-complexity threshold 10) for production and test code.
- CI builds/tests the reactor and standalone sample, runs Docker-backed integration tests, checks Protobuf compatibility with Buf, and publishes CodeQL results for same-repository changes.
- CI enforces a 50% combined line-coverage minimum; 90% is the project target, not a current gate.
- Tests use JUnit 5, behavior-focused `@DisplayName` descriptions, and parameterized tests for scenario matrices.

---

## Connection Properties Reference

The host and port are part of the JDBC URL. The only query option currently consumed by the mock client is `grpcTimeoutMillis`:

```
jdbc:mock://localhost:50051/?grpcTimeoutMillis=30000
```

| URL component / option | Type | Required | Default | Notes |
|---|---|---|---|---|
| `<host>` | hostname | ✅ | — | gRPC server host |
| `<port>` | integer | ✅ | — | gRPC server port |
| `grpcTimeoutMillis` | positive integer | ❌ | `30000` | Per-query gRPC deadline in milliseconds; also accepted through JDBC connection properties |

Other URL query parameters are parsed but currently have no effect. A lookup that exceeds the
deadline or cannot reach the backend fails with `SQLException`.

---

## Architecture Decisions

### Why gRPC?
- **Language-agnostic** — Mock service can be written in any language
- **Efficient** — Binary protocol, streaming support
- **Contract-first** — Protobuf ensures version compatibility

### Why Maven multi-module?
- **Separation of concerns** - Proxy, proto, mock logic are independent
- **Reusability** - Each module can be used separately
- **Centralized versions** - Single source of truth for dependencies

### Connection/channel lifecycle today
- **Connection objects** - Generated per `ConnectionFactory.create(...)` invocation
- **gRPC channels** - Clients share a reference-counted channel per host/port; the channel shuts down when its last client closes
- **Current limitation** - Channel lifecycle is shared by endpoint, but pooling and keep-alive tuning are not configurable

---

## Extending MockJDBC

### Add a New Dependency

1. Add to parent POM `<dependencyManagement>`:
   ```xml
   <dependency>
     <groupId>...</groupId>
     <artifactId>...</artifactId>
     <version>${maven.version.xxx}</version>
   </dependency>
   ```

2. Define property (alphabetically):
   ```xml
   <maven.version.xxx>...</maven.version.xxx>
   ```

3. In child module, declare without version:
   ```xml
   <dependency>
     <groupId>...</groupId>
     <artifactId>...</artifactId>
   </dependency>
   ```

### Add a New Test

1. Follow `@DisplayName` + `@ParameterizedTest` conventions
2. Use descriptive display names describing behavior
3. Place test in appropriate `src/test/java` folder
4. Run `mvn test` to verify

### Refactor Code

1. Follow the repository's module-boundary and implementation guidance in `.github/copilot-instructions.md`.
2. Keep changes focused and remove obsolete code when appropriate.
3. Run the relevant Maven validation and tests to verify behavior is preserved.

---

## References

- **Stewardship ADR**: [`docs/adr/ADR-0001-project-stewardship-and-gap-closure.md`](../docs/adr/ADR-0001-project-stewardship-and-gap-closure.md)
- **Sample app**: [`mockjdbc-spring-users/README.md`](../mockjdbc-spring-users/README.md)
- **Skills**: Architectural rules in `.github/skills/`
