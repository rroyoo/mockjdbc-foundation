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
- Registers/updates stubs in WireMock.
- Supports allowlist filtering by datasource and SQL deny-prefix filtering.

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
