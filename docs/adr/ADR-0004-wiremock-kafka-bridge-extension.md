# ADR-0004: WireMock extension — Kafka-driven JDBC mock mapping pipeline

- Status: Proposed
- Date: 2026-03-21
- Owners: `mockjdbc-project-steward`, `wiremock-kafka-bridge-implementer`

## Context

`mockjdbc-proxy` captures JDBC executions and publishes `MockedQuery` protobuf events to Kafka.  
`mockjdbc-mock` (the JDBC mock driver) connects to a WireMock-compatible server to resolve SQL queries into deterministic result sets.

The missing piece in the pipeline is the bridge between these two components:

- A **WireMock extension** that consumes `MockedQuery` events from Kafka, transforms them into WireMock stub mappings, and registers those stubs on the WireMock instance.
- When the mock driver later receives a query matching the SQL captured by the proxy, WireMock returns the pre-recorded result set.
- This creates a full record-replay loop: real JDBC traffic → proxy → Kafka → WireMock bridge → mock driver.

## Architecture

```
Real Application
      │
      ▼
 [mockjdbc-proxy]  ──── MockedQuery (protobuf) ──►  [Kafka topic]
                                                            │
                                                            ▼
                                               [mockjdbc-wiremock-extension]
                                               (KafkaMappingConsumer)
                                                            │
                                                  reads MockedQuery, builds
                                                  WireMock stub mapping
                                                            │
                                                            ▼
                                                    [WireMock server]
                                                  (stores JDBC stub mappings)
                                                            │
Test Application                                            │
      │                                                     │
      ▼                                                     │
 [mockjdbc-mock driver] ──── gRPC request ────────────────►┘
                                              returns serialized ResultSet
```

## Decision

Create a new Maven module `mockjdbc/mockjdbc-wiremock` implementing a WireMock extension that:

1. Reads `MockedQuery` protobuf events from a Kafka topic.
2. Transforms each event into a WireMock stub mapping keyed by SQL + datasource.
3. Registers the stub on the WireMock instance (via WireMock Java API or Admin REST API).
4. Exposes a lifecycle-managed consumer (`KafkaMappingConsumer`) that can be started/stopped.
5. Supports configurable topic, consumer group, and datasource-level filtering.

## Mapping strategy

Each `MockedQuery` event is mapped to a WireMock stub as follows:

- **Stub identity key**: `datasource_id + "::" + normalized_sql`
- **Request matcher**: gRPC-style body or HTTP JSON request matching `sql` + optional `datasource_id`.
- **Response payload**: serialized `SerializedResultSet` from the event, encoded in the stub response body.
- **Idempotency**: if a mapping for the same key already exists, it is overwritten (last-write-wins by default, configurable).
- **Error mapping**: events with `QUERY_EXECUTION_STATUS_ERROR` create fault/error stubs.

## Stub mapping structure

```json
{
  "request": {
    "method": "POST",
    "urlPattern": "/mockjdbc/query",
    "bodyPatterns": [
      { "matchesJsonPath": "$.sql", "value": "<normalized sql>" },
      { "matchesJsonPath": "$.datasourceId", "value": "<datasource_id>" }
    ]
  },
  "response": {
    "status": 200,
    "body": "<base64-encoded SerializedResultSet proto bytes>",
    "headers": { "Content-Type": "application/x-protobuf" }
  }
}
```

## Module layout

```
mockjdbc/
  mockjdbc-wiremock/
    pom.xml
    src/main/java/
      io/github/rroyoo/mockjdbc/wiremock/
        KafkaMappingConsumer.java      (lifecycle: start/stop)
        MockedQueryStubMapper.java     (MockedQuery → WireMock stub)
        WireMockMappingRegistrar.java  (registers stubs on WireMock)
        KafkaMappingConsumerConfig.java (config record)
    src/test/java/
      io/github/rroyoo/mockjdbc/wiremock/
        MockedQueryStubMapperTest.java
        KafkaMappingConsumerTest.java  (unit, mocked consumer)
        KafkaMappingConsumerIntegrationTest.java (Testcontainers)
    README.md
```

## Dependencies

- `mockjdbc-proto` — `MockedQuery` and `SerializedResultSet` message types.
- `kafka-clients` — Kafka consumer.
- `wiremock` — WireMock Java client/server API.
- `junit-jupiter` + `mockito-core` + `testcontainers:kafka` — testing.

## Lifecycle

`KafkaMappingConsumer` is `AutoCloseable` and controls a single-thread consumer loop:

```java
var config = new KafkaMappingConsumerConfig(
    bootstrapServers,
    "mockjdbc.query.events",
    "mockjdbc-wiremock-bridge",
    WireMock.create().host("localhost").port(8080).build()
);

try (var consumer = new KafkaMappingConsumer(config)) {
    consumer.start();
    // ... WireMock instance now receives stubs as Kafka events arrive
}
```

## Filtering

- Optional datasource filter: only create stubs for events from specific datasource ids.
- Optional SQL prefix filter: skip health-check or internal queries.
- Both filters default to accept-all.

## Acceptance checklist

- [x] Create `mockjdbc-wiremock` Maven module and add to reactor.
- [x] Add `MockedQueryStubMapper` with mapping logic and tests.
- [x] Add `WireMockMappingRegistrar` with registration logic and tests.
- [x] Add `KafkaMappingConsumer` with lifecycle management and tests.
- [x] Add `KafkaMappingConsumerConfig` with validation.
- [ ] Add integration test with Testcontainers Kafka + WireMock (implemented; executes when Docker is available).
- [x] Add `README.md` with usage guide and integration examples.
- [x] Update `ADR-0001` backlog with this new module.
- [x] Update reactor `pom.xml` to include new module.

## Consequences

### Positive
- Closes the loop between proxy capture and mock driver response.
- Makes record-replay JDBC mocking possible without manual stub authoring.
- Decouples capture (proxy) from replay (mock driver) via Kafka.

### Trade-offs
- Introduces WireMock as a required runtime dependency for replay mode.
- Stub mapping idempotency and SQL normalization need careful design to avoid ghost stubs.
- Kafka consumer lifecycle needs proper shutdown to avoid thread leaks in tests.

## Related

- `docs/adr/ADR-0001-project-stewardship-and-gap-closure.md`
- `docs/adr/ADR-0002-jdbc-proxy-capture-and-kafka-event-pipeline.md`
- `docs/adr/ADR-0003-mock-driver-scope-and-jdbc-compatibility.md`
