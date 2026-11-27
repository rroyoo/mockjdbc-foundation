---
id: replaying-with-the-mock-driver
title: Replaying with the mock driver
sidebar_position: 3
---

`mockjdbc-mock` is a real `java.sql.Driver`. It self-registers via `META-INF/services`, so `DriverManager` discovers it automatically once it's on the classpath — no manual `Class.forName(...)` call is required.

## Connection URL

```
jdbc:mock://<host>:<port>/?host=<host>&port=<port>&keepAliveTime=<seconds>&keepAliveTimeUnit=<unit>
```

```java
Connection connection = DriverManager.getConnection(
        "jdbc:mock://localhost:50051/?host=localhost&port=50051&keepAliveTime=60&keepAliveTimeUnit=SECONDS");
```

| Property | Type | Required | Default | Notes |
|---|---|---|---|---|
| `host` | String | yes | — | gRPC server host (the WireMock gRPC extension endpoint) |
| `port` | String (parsed as int) | yes | — | gRPC server port |
| `keepAliveTime` | long | no | `30` | Seconds between gRPC keep-alive pings |
| `keepAliveTimeUnit` | `TimeUnit` enum name | no | `SECONDS` | e.g. `SECONDS`, `MINUTES` |

## How a query is resolved

1. Your application executes a `Statement`/`PreparedStatement`/`CallableStatement` as usual.
2. The ByteBuddy-generated statement implementation calls `GrpcMockQueryClient`, which invokes `MockQueryService.FindMock` over gRPC against the configured WireMock gRPC endpoint.
3. `ResultSetFactory` converts the returned payload into a JDBC `ResultSet` (or update count / generated keys), coercing values to their declared SQL type.

Because the mock driver runs against WireMock stubs rather than a real database, replay is **stateless**: `commit`/`rollback` are tracked but are no-ops, and there is no transactional persistence between statements. See [Known limitations](../reference/known-limitations.md).

## Minimal example

```java
import java.sql.*;

try (Connection connection =
        DriverManager.getConnection("jdbc:mock://localhost:50051")) {
    try (PreparedStatement statement =
            connection.prepareStatement("SELECT id, name FROM users WHERE id = ?")) {
        statement.setLong(1, 42L);
        try (ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                System.out.println(resultSet.getLong("id") + " " + resultSet.getString("name"));
            }
        }
    }
}
```

This returns whatever row(s) the WireMock gRPC backend has registered as a stub for that normalized SQL — see the [WireMock bridge guide](../guides/wiremock-bridge.md) for how those stubs get created from captured traffic.

## Spring Boot example

The sample app's `mockjdbc` profile configures a plain `DataSource` with the mock driver:

```yaml
spring:
  datasource:
    url: jdbc:mock://${MOCKJDBC_GRPC_HOST:localhost}:${MOCKJDBC_GRPC_PORT:8088}
```

```bash
docker compose up -d wiremock-grpc
mvn spring-boot:run -Dspring-boot.run.profiles=mockjdbc
```

Next: see the [supported JDBC surface](../reference/supported-jdbc-surface.md) for exactly which `Connection`/`Statement` methods are implemented, or follow the [end-to-end tutorial](../guides/end-to-end-tutorial.md) to capture real traffic and replay it with this driver.
