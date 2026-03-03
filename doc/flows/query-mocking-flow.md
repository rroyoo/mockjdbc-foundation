# Flow: Query Mocking

## Scope
This flow describes how SQL is translated into a `MockedQuery` response through the gRPC lookup service.

## Sequence

```mermaid
sequenceDiagram
  autonumber
  participant App as Application
  participant Driver as MockDriver
  participant Conn as MockConnection
  participant Adapter as DefaultMockQueryServiceAdapter
  participant Svc as MockQueryService (gRPC)

  App->>Driver: connect(url, properties)
  Driver->>Conn: create connection
  App->>Conn: execute SQL / prepared / callable
  Conn->>Adapter: findMockedQuery(request)
  Adapter->>Svc: findMock(QueryLookupRequest)
  Svc-->>Adapter: MockedQuery
  Adapter-->>Conn: MockedQuery
  Conn-->>App: mock query result
```

## Request Mapping Rules

| Statement Type | Request Fields |
|---|---|
| Plain statement | `sql` |
| Prepared statement | `sql`, `parameters[]` |
| Callable statement | `callSql`, `parameters[]` |

## Property Dependencies

| Property | Usage |
|---|---|
| `host` | gRPC target host |
| `port` | gRPC target port |
| `keepAlive*` | Channel keep-alive tuning |
| `idleTimeout*` | Channel idle lifecycle |

## Failure Points
- Missing required property (`host`, `port`).
- Invalid `TimeUnit` value in timeout properties.
- gRPC channel unavailable.
- Service returns error or no matching mock.

## Notes
- Keep-alive and retry are configured in `DefaultMockQueryServiceAdapter`.
- Channel is reused per adapter instance and closed when connection closes.

