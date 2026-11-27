# mockjdbc-wiremock

WireMock extension module that consumes `MockedQuery` protobuf events from Kafka and upserts WireMock stub mappings for replay.

## Extension discovery and configuration

The shaded JAR registers `WireMockKafkaBridgeExtensionFactory` through WireMock's `ExtensionFactory` service descriptor. The factory creates the bridge extension only when `MOCKJDBC_KAFKA_BOOTSTRAP_SERVERS` is set; the extension controls the consumer lifecycle (`start()` / `stop()`).

| Environment variable | Required | Default |
|---|---|---|
| `MOCKJDBC_KAFKA_BOOTSTRAP_SERVERS` | yes | — |
| `MOCKJDBC_KAFKA_TOPIC` | no | `mockjdbc.query.events` |
| `MOCKJDBC_KAFKA_GROUP_ID` | no | `mockjdbc-wiremock-bridge` |
| `MOCKJDBC_KAFKA_POLL_TIMEOUT_MS` | no | `250` |
| `MOCKJDBC_DATASOURCE_ALLOWLIST` | no | empty |
| `MOCKJDBC_SQL_DENY_PREFIXES` | no | empty |

## What it does

- Reads `MockedQuery` events from Kafka topic.
- Normalizes SQL and builds deterministic stub keys:
  - `<datasource_id>::<normalized_sql>`
  - Parameters (when present) are appended to the key for deduplication: `::p<index>=<value>`
- The lookup request does not contain a datasource ID; the datasource ID is used in the mapping identity and can be filtered with `MOCKJDBC_DATASOURCE_ALLOWLIST`.
## Main classes

- `WireMockKafkaBridgeExtension`
- `KafkaMappingConsumerConfig`
- `KafkaMappingConsumer`
- `MockedQueryStubMapper`
- `WireMockMappingRegistrar`

## Quick usage

For embedded use or tests, create the WireMock server first, then pass it to a consumer:

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
    // Keep application running while bridge consumes events.
}
```

## SQL normalization

- trim
- lowercase
- collapse whitespace
- strip trailing semicolons

Example:

- Input: `" SELECT  * FROM users; "`
- Normalized: `"select * from users"`

## Tests

```bash
cd mockjdbc
mvn -pl mockjdbc-wiremock -am test
```

Integration test requires Docker (Testcontainers). If Docker is unavailable, it is skipped.

## Packaging and runtime requirements for publication

This module is shaded (`maven-shade-plugin`) because it is intended to be dropped into the **WireMock extensions directory** as a single jar, discovered through the `META-INF/services/com.github.tomakehurst.wiremock.extension.ExtensionFactory` entry.

The shaded jar deliberately **excludes `io.grpc:*` and `com.google.api.grpc:proto-google-common-protos`**. Those classes are expected to already be present at runtime, supplied by the external **WireMock gRPC extension** that the host WireMock process loads alongside this one. If you deploy `mockjdbc-wiremock` without also installing a compatible WireMock gRPC extension, class-loading for gRPC types will fail.

When consuming this module from Maven Central:
- Add the WireMock gRPC extension as a sibling dependency (or extensions-directory jar) wherever you run WireMock.
- Do not add `mockjdbc-wiremock` as a transitive compile dependency of an application expecting a complete, self-contained gRPC stack — it is built for the WireMock extensions model, not general-purpose library consumption.
