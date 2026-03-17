# mockjdbc-proxy

Utility module for JDBC interception based on `datasource-proxy`.

## Listener

`JdbcQueryCaptureListener` implements datasource-proxy `QueryExecutionListener` and captures immutable events (`JdbcQueryInterceptedEvent`) for each executed SQL.

It also emits protobuf events (`MockedQuery`) asynchronously through an injectable external producer.

### Quick usage

```java
import io.github.rroyoo.mockjdbc.proxy.JdbcQueryCaptureListener;
import net.ttddyy.dsproxy.support.ProxyDataSourceBuilder;

JdbcQueryCaptureListener listener = new JdbcQueryCaptureListener();

DataSource proxy = ProxyDataSourceBuilder
        .create(realDataSource)
        .name("app-ds")
        .listener(listener)
        .build();

// Execute JDBC statements using proxy DataSource

listener.events().forEach(event -> {
    System.out.println(event.sql());
    System.out.println(event.parameters());
    System.out.println(event.elapsedTimeMillis());
});
```

## Verify

```bash
mvn -pl mockjdbc-proxy test
```

### Run load-oriented tests

```bash
mvn -pl mockjdbc-proxy -Dtest=AsyncMockedQueryEventDispatcherLoadTest test
```

## Async Producer Integration

```java
import io.github.rroyoo.mockjdbc.proxy.AsyncDispatchConfig;
import io.github.rroyoo.mockjdbc.proxy.JdbcQueryCaptureListener;
import io.github.rroyoo.mockjdbc.proxy.MockedQueryEventProducer;

MockedQueryEventProducer kafkaProducer = mockedQuery -> {
    // Your external producer implementation (Kafka, Pulsar, etc.)
    // kafkaTemplate.send("mockjdbc.events", mockedQuery.toByteArray());
};

AsyncDispatchConfig config = new AsyncDispatchConfig(
        8192,                                 // ring buffer capacity
        4,                                    // sender thread pool size
        AsyncDispatchConfig.OverflowStrategy.DROP_OLDEST,
        512                                   // rows per ResultSet chunk
);

JdbcQueryCaptureListener listener = new JdbcQueryCaptureListener(
        event -> {},
        kafkaProducer,
        config
);
```

## Memory Strategy for ResultSet Capture

- The ByteBuddy wrapper captures consumed rows in batches (`rowBatchSize`) instead of keeping the full `ResultSet` in memory.
- Each batch is emitted as a `MockedQuery` protobuf event with shared metadata and the current row chunk.
- This avoids unbounded heap growth on large queries.
- Update/delete/procedure result counts are emitted immediately as lightweight `SerializedResultSet` with `update_count`.

## Dispatcher Stats

`AsyncMockedQueryEventDispatcher#stats()` exposes a snapshot:

- `offered`: events accepted by publish attempts
- `sent`: events successfully sent by sender threads
- `dropped`: events dropped by overflow strategy
- `failed`: send attempts that threw exceptions
- `queued`: current in-memory queue size

## Tiny Benchmark Harness

Manual benchmark runner is available at `AsyncDispatcherBenchmarkRunner`.

```bash
mvn -pl mockjdbc-proxy -DskipTests test-compile
java -cp "mockjdbc-proxy/target/test-classes:mockjdbc-proxy/target/classes:mockjdbc-proto/target/classes" io.github.rroyoo.mockjdbc.proxy.AsyncDispatcherBenchmarkRunner
```

