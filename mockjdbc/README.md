# MockJDBC Technical Architecture

**Technical documentation for developers and integrators.**

For **functional requirements and use cases**, see the main [README.md](../README.md).

---

## Overview

MockJDBC is a multi-module Maven project that implements a JDBC-compliant mock driver backed by gRPC for query resolution.

---

## Modules

### `mockjdbc-proxy`
**DataSource interception and event publishing**

Provides utilities to intercept JDBC query execution and emit structured events for logging/observability.

- `DataSourceProxyFactory` — Wraps real DataSources with proxy layers
- `QueryExecutionEvent` — Immutable event record (sql, parameters, execution time, success, exception)
- `QueryExecutionEventListener` — Subscriber interface for events
- `LoggingQueryExecutionEventListener` — Built-in listener that logs events
- `DefaultQueryExecutionEventMapper` — Converts internal events to `QueryExecutionEvent`

**Dependencies:**
- `datasource-proxy` 1.11.0 — Third-party DataSource interception library

### `mockjdbc-proto`
**gRPC service contracts and Protobuf definitions**

Defines the protocol between MockJDBC and the remote query mock service.

- `proto/` — Protobuf `.proto` files defining `MockQueryService` RPC interface
- Generated stubs — `MockQueryServiceGrpc`, request/response messages
- Protocol: Query lookup via `findMock(QueryLookupRequest) -> MockedQuery`

**Key files:**
- `MockQueryService.proto` — Service definition
- Generated classes in `target/generated-sources/protobuf/`

### `mockjdbc-mock`
**JDBC driver implementation and gRPC adapter**

Implements the JDBC Driver interface and handles connection lifecycle.

**Key classes:**

| Class | Responsibility |
|---|---|
| `MockDriver` | JDBC `Driver` implementation; accepts `jdbc:mock://` URLs |
| `MockConnection` | JDBC `Connection`; creates statements |
| `MockStatement` | Unified `Statement` + `PreparedStatement` + `CallableStatement` |
| `MockConnectionPool` | In-memory cache of connections by JDBC URL |
| `MockConnectionProperties` | Enum of configuration properties (host, port, timeouts) |
| `DefaultMockQueryServiceAdapter` | gRPC client; calls `MockQueryService.findMock()` |

**Flow:**
1. Application calls `DriverManager.getConnection("jdbc:mock://...")`
2. `MockDriver.connect()` parses URL and creates `MockConnection`
3. `MockConnection.createStatement()` returns `MockStatement`
4. Statement execution triggers `DefaultMockQueryServiceAdapter.findMockedQuery()`
5. Adapter calls gRPC service and returns mocked results

---

## Build Structure

### Parent POM (`pom.xml`)

**Centralized version management** following `maven-management` skill rules:

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
| `maven.version.datasource-proxy` | `1.11.0` | Query interception library |
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

### Full Verification

```bash
mvn clean verify
```

Compiles, tests, and validates:
- ✅ All modules compile
- ✅ All tests pass
- ✅ No compilation warnings
- ✅ Code follows quality standards

---

## Quality Standards

### Code Cleanliness
Enforced via `java-modernizer` skill:
- ✅ No unused imports
- ✅ No unused variables
- ✅ No dead/commented code (exception: explanatory comments for workarounds)
- ✅ No unused private methods/fields

### Testing
Enforced via `tdd-expert` skill:
- ✅ `@DisplayName` mandatory on every test
- ✅ `@ParameterizedTest` for multiple scenarios
- ✅ Clear Arrange-Act-Assert structure
- ✅ No flaky tests (<100ms each)
- ✅ ≥80% code coverage for business logic

### Maven Discipline
Enforced via `maven-management` skill:
- ✅ Centralized versions in parent POM
- ✅ Properties sorted alphabetically
- ✅ Canonical POM element order
- ✅ No hardcoded versions in child modules
- ✅ Clear dependency scopes (test, provided, runtime)

---

## Connection Properties Reference

When creating a JDBC connection, pass configuration as URL parameters:

```
jdbc:mock://localhost:50051/?host=localhost&port=50051&keepAliveTime=60&keepAliveTimeUnit=SECONDS
```

All properties are read into `java.util.Properties` and passed to `DefaultMockQueryServiceAdapter`.

| Property | Type | Required | Default | Notes |
|---|---|---|---|---|
| `host` | String | ✅ | — | gRPC server host |
| `port` | String (parseInt) | ✅ | — | gRPC server port |
| `keepAliveTime` | long | ❌ | `30` | Seconds between keep-alive pings |
| `keepAliveTimeUnit` | TimeUnit | ❌ | `SECONDS` | Enum name (e.g., `SECONDS`, `MINUTES`) |
| `keepAliveTimeout` | long | ❌ | `10` | Seconds to wait for keep-alive response |
| `keepAliveTimeoutTimeUnit` | TimeUnit | ❌ | `SECONDS` | Enum name |
| `idleTimeout` | long | ❌ | `10` | Channel idle threshold |
| `idleTimeoutTimeUnit` | TimeUnit | ❌ | `MINUTES` | Enum name |

---

## Architecture Decisions

### Why gRPC?
- **Language-agnostic** — Mock service can be written in any language
- **Efficient** — Binary protocol, streaming support
- **Contract-first** — Protobuf ensures version compatibility

### Why Maven multi-module?
- **Separation of concerns** — Proxy, proto, mock logic are independent
- **Reusability** — Each module can be used separately
- **Centralized versions** — Single source of truth for dependencies

### Why connection pooling?
- **Performance** — Reuse gRPC channels instead of creating new ones
- **Resource efficiency** — Limits concurrent connections to mock service
- **Simple caching strategy** — By-URL caching is sufficient for tests

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

1. Check `java-modernizer` skill for modernization opportunities
2. Remove unused imports, variables, dead code
3. Run `mvn clean compile` to catch warnings
4. Run tests to ensure behavior is preserved

---

## References

- **Functional docs**: Main [README.md](../README.md)
- **Use cases**: [`doc/use-cases.md`](../doc/use-cases.md)
- **Flows**: [`doc/flows/query-mocking-flow.md`](../doc/flows/query-mocking-flow.md)
- **Skills**: Architectural rules in `.github/skills/`

