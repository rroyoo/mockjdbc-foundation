# mockjdbc-proxy

Utility module for JDBC interception based on `datasource-proxy`.

## Listener

`JdbcQueryCaptureListener` implements datasource-proxy `QueryExecutionListener` and captures immutable events (`JdbcQueryInterceptedEvent`) for each executed SQL.

It emits protobuf events (`MockedQuery`) asynchronously through an injectable producer and enriches each event with proxy metadata:
- `datasource_id`
- `elapsed_time_millis`
- `status`
- `row_count`
- `update_count`
- `event_id`
- `observed_at`

## Multi-datasource wrapping

Use `JdbcProxyDataSourceFactory` to wrap each datasource with an explicit datasource id.

```java
import io.github.rroyoo.mockjdbc.proxy.AsyncDispatchConfig;
import io.github.rroyoo.mockjdbc.proxy.JdbcProxyDataSourceFactory;

DataSource usersDataSource = ...;
DataSource ordersDataSource = ...;

var usersBinding = JdbcProxyDataSourceFactory.wrap(
        usersDataSource,
        "users-primary",
        event -> {},
        mockedQuery -> {},
        AsyncDispatchConfig.defaults()
);

var ordersBinding = JdbcProxyDataSourceFactory.wrap(
        ordersDataSource,
        "orders-replica",
        event -> {},
        mockedQuery -> {},
        AsyncDispatchConfig.defaults()
);

DataSource proxiedUsers = usersBinding.dataSource();
DataSource proxiedOrders = ordersBinding.dataSource();
```

## Kafka producer integration

Use `KafkaMockedQueryEventProducer` to publish `MockedQuery` protobuf bytes keyed by datasource.

```java
import io.github.rroyoo.mockjdbc.proxy.KafkaMockedQueryEventProducer;

Properties kafkaProperties = new Properties();
kafkaProperties.setProperty("bootstrap.servers", "localhost:9092");
kafkaProperties.setProperty("acks", "all");
kafkaProperties.setProperty("retries", "3");

var kafkaProducer = KafkaMockedQueryEventProducer.create(
        kafkaProperties,
        "mockjdbc.query.events",
        KafkaMockedQueryEventProducer.datasourceKeyResolver()
);

kafkaProducer.send(mockedQuery);
```

## Spring-style integration idea

1. Define original datasource beans.
2. Wrap each datasource with `JdbcProxyDataSourceFactory.wrap(...)` and a stable datasource id.
3. Expose `binding.dataSource()` as the datasource used by `JdbcTemplate` or JPA.
4. Close bindings on shutdown to stop async dispatch threads.

## Verify

```bash
mvn -pl mockjdbc-proxy test
```

## Async dispatch tuning

`AsyncMockedQueryEventDispatcher#stats()` exposes:
- `offered`
- `sent`
- `dropped`
- `failed`
- `queued`

Tune `AsyncDispatchConfig` according to throughput and memory constraints.

## Memory strategy for ResultSet capture

- The ByteBuddy wrapper captures consumed rows in batches (`rowBatchSize`) instead of storing full result sets.
- Each batch is emitted as a `MockedQuery` protobuf event with shared metadata.
- Update/delete/procedure counts are emitted as lightweight `SerializedResultSet` with `update_count`.

## Tiny benchmark harness

Manual benchmark runner is available at `AsyncDispatcherBenchmarkRunner`.

```bash
mvn -pl mockjdbc-proxy -DskipTests test-compile
java -cp "mockjdbc-proxy/target/test-classes:mockjdbc-proxy/target/classes:mockjdbc-proto/target/classes" io.github.rroyoo.mockjdbc.proxy.AsyncDispatcherBenchmarkRunner
```
