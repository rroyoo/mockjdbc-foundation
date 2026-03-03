# Functional Overview

## Goal
Provide a JDBC-compatible mock flow where SQL statements are resolved to mocked responses through a remote gRPC lookup service.

## Main Functional Capabilities
- Open JDBC connections through `MockDriver` using URL-based properties.
- Execute statement lookups (plain, prepared, callable) against `MockQueryService`.
- Return mocked query definitions as `MockedQuery` responses.
- Emit query execution events from proxy components for logging/observability integrations.

## Functional Actors

| Actor | Responsibility |
|---|---|
| Application | Issues JDBC calls |
| `MockDriver` | Accepts JDBC URL and creates mock connections |
| `MockConnection` | Creates statements and delegates lookup calls |
| `DefaultMockQueryServiceAdapter` | Calls gRPC `findMock` endpoints |
| Mock Query Service | Returns matching mocked query response |

## Functional Inputs

| Input | Source | Required |
|---|---|---|
| JDBC URL | Application | yes |
| SQL string | Statement execution | yes |
| Statement parameters | Prepared/Callable statements | conditional |
| `host` + `port` | Connection properties | yes |

## Functional Output
- `MockedQuery` result resolved from the gRPC service for the requested SQL (+ parameters if present).

## Happy Path Summary
1. Application requests a connection using a compatible URL.
2. `MockDriver` validates URL and creates a connection from the pool.
3. Statement execution builds `QueryLookupRequest`.
4. Adapter calls `MockQueryServiceGrpc.MockQueryServiceBlockingStub.findMock(...)`.
5. Service returns `MockedQuery`.

## Error Cases (Functional)
- Invalid/unsupported URL -> connection rejected.
- Missing `host` or `port` property -> adapter initialization fails.
- Invalid timeout/unit property -> adapter initialization fails.
- gRPC service unreachable -> lookup fails at runtime.

