# mockjdbc-wiremock

WireMock extension module that consumes `MockedQuery` protobuf events from Kafka and upserts WireMock stub mappings for replay.

## Extension entrypoint

`WireMockKafkaBridgeExtension` implements `com.github.tomakehurst.wiremock.extension.Extension` and controls bridge lifecycle:
- `start()` -> starts Kafka consumer loop
- `stop()` -> stops and closes Kafka consumer

You can register it on a `WireMockServer` options object.

```java
WireMockServer wireMock = new WireMockServer(
        options().extensions(
                new WireMockKafkaBridgeExtension(
                        KafkaMappingConsumerConfig.defaults(
                                "localhost:9092",
                                "mockjdbc.query.events",
                                "mockjdbc-wiremock-bridge",
                                wireMockServerReference
                        )
                )
        )
);
```

## What it does

- Reads `MockedQuery` events from Kafka topic.
- Normalizes SQL and builds deterministic stub keys:
  - `<datasource_id>::<normalized_sql>`
  - Parameters (when present) are appended to the key for deduplication: `::p<index>=<value>`
## Main classes

- `WireMockKafkaBridgeExtension`
- `KafkaMappingConsumerConfig`
- `KafkaMappingConsumer`
- `MockedQueryStubMapper`
- `WireMockMappingRegistrar`

## Quick usage

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
