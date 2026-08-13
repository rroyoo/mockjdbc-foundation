# MockJDBC Technical Architecture

**Technical documentation for developers and integrators.**

For project stewardship context and backlog status, see [`docs/adr/ADR-0001-project-stewardship-and-gap-closure.md`](../docs/adr/ADR-0001-project-stewardship-and-gap-closure.md).

---

## Overview

MockJDBC is a multi-module Maven project that implements a JDBC-compliant mock driver backed by gRPC for query resolution.

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

All properties are parsed into `MockConfig` and used by `ConnectionFactory` / `GrpcMockQueryClient`.

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
- **Separation of concerns** - Proxy, proto, mock logic are independent
- **Reusability** - Each module can be used separately
- **Centralized versions** - Single source of truth for dependencies

### Connection/channel lifecycle today
- **Connection objects** - Generated per `ConnectionFactory.create(...)` invocation
- **gRPC calls** - `GrpcMockQueryClient` creates a channel per lookup and shuts it down after the call
- **Current limitation** - No long-lived connection/channel pooling layer is implemented yet

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

- **Stewardship ADR**: [`docs/adr/ADR-0001-project-stewardship-and-gap-closure.md`](../docs/adr/ADR-0001-project-stewardship-and-gap-closure.md)
- **Sample app**: [`mockjdbc-spring-users/README.md`](../mockjdbc-spring-users/README.md)
- **Skills**: Architectural rules in `.github/skills/`
