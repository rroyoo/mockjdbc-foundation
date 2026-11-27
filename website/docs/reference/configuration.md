---
id: configuration
title: Configuration reference
sidebar_position: 1
---

## Mock driver connection URL (`mockjdbc-mock`)

```
jdbc:mock://<host>:<port>/?host=<host>&port=<port>&keepAliveTime=<seconds>&keepAliveTimeUnit=<unit>
```

| Property | Type | Required | Default | Notes |
|---|---|---|---|---|
| `host` | String | yes | — | gRPC server host (the WireMock gRPC extension endpoint) |
| `port` | String (parsed as int) | yes | — | gRPC server port |
| `keepAliveTime` | long | no | `30` | Seconds between gRPC keep-alive pings |
| `keepAliveTimeUnit` | `TimeUnit` enum name | no | `SECONDS` | e.g. `SECONDS`, `MINUTES` |

## Proxy agent (`mockjdbc-proxy`)

The agent has no global configuration file — behavior is configured programmatically per `DataSource` registration:

```java
JdbcAgentRegistry.register(
        dataSource,
        "users-primary",          // required explicit datasource id
        localEventConsumer,
        mockedQueryEventProducer,
        asyncDispatchConfig
);
```

### `AsyncDispatchConfig`

| Field | Type | Default (`AsyncDispatchConfig.defaults()`) | Notes |
|---|---|---|---|
| `ringBufferCapacity` | int | `8192` | Rounded up to the next power of two; floored at `2`. |
| `senderThreads` | int | `2` | Threads draining the ring buffer to the configured producer. |
| `overflowStrategy` | `OverflowStrategy` | `DROP_OLDEST` | Also `DROP_NEWEST`. |
| `rowBatchSize` | int | `512` | Rows per `MockedQuery` event batch during ResultSet capture. |

Call `registration.close()` to stop sender threads cleanly. `AsyncMockedQueryEventDispatcher.stats()` exposes `offered`, `sent`, `dropped`, `failed`, `queued` counters for monitoring backpressure.

### Kafka producer

```java
Properties props = new Properties();
props.setProperty("bootstrap.servers", "localhost:9092");
props.setProperty("acks", "all");

MockedQueryEventProducer producer = KafkaMockedQueryEventProducer.create(
        props,
        "mockjdbc.query.events",
        KafkaMockedQueryEventProducer.datasourceKeyResolver()
);
```

Keys are datasource-oriented by default, preserving per-datasource ordering.

### Sample app environment overrides

The `mockjdbc-spring-users` sample's `proxy` profile reads these environment variables (see `application-proxy.yml`):

| Variable | Default |
|---|---|
| `MOCKJDBC_PROXY_KAFKA_BOOTSTRAP` | `localhost:9094` |
| `MOCKJDBC_PROXY_KAFKA_TOPIC` | `mockjdbc.query.events` |
| `MOCKJDBC_PROXY_KAFKA_ACKS` | `all` |
| `MOCKJDBC_PROXY_KAFKA_RETRIES` | `3` |

The `mockjdbc` profile reads:

| Variable | Default |
|---|---|
| `MOCKJDBC_GRPC_HOST` | `localhost` |
| `MOCKJDBC_GRPC_PORT` | `8088` |

## WireMock bridge (`mockjdbc-wiremock`)

```java
KafkaMappingConsumerConfig.defaults(
        "localhost:9092",            // Kafka bootstrap servers
        "mockjdbc.query.events",      // topic
        "mockjdbc-wiremock-bridge",   // consumer group id
        wireMockServerReference
);
```

SQL normalization applied before building stub keys is fixed (not configurable): trim, lowercase, collapse whitespace, strip trailing semicolons.
