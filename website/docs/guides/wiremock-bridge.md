---
id: wiremock-bridge
title: The WireMock bridge
sidebar_position: 1
---

`mockjdbc-wiremock` consumes `MockedQuery` protobuf events from Kafka and upserts them as deterministic WireMock stub mappings, closing the loop between the proxy agent's captures and the mock driver's replays.

## Automatic extension discovery

The shaded JAR registers `WireMockKafkaBridgeExtensionFactory` through WireMock's `ExtensionFactory` service descriptor. WireMock creates the bridge extension when `MOCKJDBC_KAFKA_BOOTSTRAP_SERVERS` is set in its environment; the extension owns the consumer lifecycle.

| Environment variable | Required | Default | Purpose |
|---|---|---|---|
| `MOCKJDBC_KAFKA_BOOTSTRAP_SERVERS` | yes | — | Enables the bridge and sets Kafka bootstrap servers. |
| `MOCKJDBC_KAFKA_TOPIC` | no | `mockjdbc.query.events` | Topic to consume. |
| `MOCKJDBC_KAFKA_GROUP_ID` | no | `mockjdbc-wiremock-bridge` | Consumer group. |
| `MOCKJDBC_KAFKA_POLL_TIMEOUT_MS` | no | `250` | Consumer poll timeout. |
| `MOCKJDBC_DATASOURCE_ALLOWLIST` | no | empty | Optional comma-separated datasource IDs to accept. |
| `MOCKJDBC_SQL_DENY_PREFIXES` | no | empty | Optional comma-separated normalized SQL prefixes to reject. |

For example, configure the WireMock container with:

```yaml
environment:
  MOCKJDBC_KAFKA_BOOTSTRAP_SERVERS: kafka:9092
  MOCKJDBC_KAFKA_TOPIC: mockjdbc.query.events
  MOCKJDBC_KAFKA_GROUP_ID: mockjdbc-wiremock-bridge
```

## Driving the consumer directly

For embedded use or tests, create the WireMock server first and pass it to the consumer:

```java
WireMockServer wireMock = new WireMockServer(8080);
wireMock.start();

KafkaMappingConsumerConfig config = KafkaMappingConsumerConfig.defaults(
        "localhost:9092",
        "mockjdbc.query.events",
        "mockjdbc-wiremock-bridge",
        wireMock
);

try (KafkaMappingConsumer consumer = new KafkaMappingConsumer(config)) {
    consumer.start();
    // Keep application running while the bridge consumes events.
}
```

## How events become stubs

1. `KafkaMappingConsumer` reads `MockedQuery` events from the configured Kafka topic.
2. `MockedQueryStubMapper` normalizes the SQL and builds a deterministic stub key: `<datasource_id>::<normalized_sql>`. Parameters, when present, are appended for deduplication (`::p<index>=<value>`).
3. `WireMockMappingRegistrar` upserts (registers or replaces) the corresponding WireMock gRPC stub.

### SQL normalization

Normalization is: trim, lowercase, collapse whitespace, strip trailing semicolons.

| Input | Normalized |
|---|---|
| `" SELECT  * FROM users; "` | `"select * from users"` |

This is the same normalization the mock driver's query lookup expects, so a captured query and its later replay always resolve to the same stub key.

## Packaging and runtime requirements

`mockjdbc-wiremock` is shipped as a **shaded jar** meant to be dropped into the WireMock extensions directory, discovered via the `META-INF/services/com.github.tomakehurst.wiremock.extension.ExtensionFactory` entry. It deliberately **excludes `io.grpc:*`** classes — those must be supplied by the external WireMock gRPC extension that the host WireMock process also loads.

:::info
When consuming this module from Maven Central, add the WireMock gRPC extension alongside it in your WireMock extensions directory. Do not add `mockjdbc-wiremock` as a transitive compile dependency of an application expecting a self-contained gRPC stack — it is built for the WireMock extensions model, not general-purpose library consumption.
:::

Next: see the [end-to-end tutorial](end-to-end-tutorial.md) to watch captured traffic become a replayable stub, or the [configuration reference](../reference/configuration.md) for all `KafkaMappingConsumerConfig` options.
