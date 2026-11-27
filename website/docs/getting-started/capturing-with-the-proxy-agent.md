---
id: capturing-with-the-proxy-agent
title: Capturing SQL with the proxy agent
sidebar_position: 2
---

`mockjdbc-proxy` is a **Java agent** — it instruments your `DataSource` at the bytecode level, so no DataSource wrapping is required in application code.

## 1. Attach the agent

```bash
java -javaagent:/path/to/mockjdbc-proxy.jar -jar my-app.jar
```

## 2. Register a DataSource

```java
import io.github.rroyoo.mockjdbc.proxy.*;

DataSource usersDataSource = ...;

JdbcCaptureRegistration registration = JdbcAgentRegistry.register(
        usersDataSource,
        "users-primary",          // logical datasource id (required, no implicit fallback)
        event -> {},               // local JdbcQueryInterceptedEvent consumer
        kafkaProducer,              // MockedQueryEventProducer, e.g. KafkaMockedQueryEventProducer
        AsyncDispatchConfig.defaults()
);

// Use usersDataSource normally — every Statement/PreparedStatement/CallableStatement
// execution is captured and published automatically.
```

Every DataSource registration requires an **explicit datasource id** — there is no implicit fingerprinting fallback at the agent level. If you need a hash-based identity derived from connection metadata (URL + user + driver), call `DatasourceIdentityResolver.resolve(alias, beanName, dataSource)` yourself.

## 3. Wire a Kafka producer

```java
Properties props = new Properties();
props.setProperty("bootstrap.servers", "localhost:9092");
props.setProperty("acks", "all");

MockedQueryEventProducer kafkaProducer = KafkaMockedQueryEventProducer.create(
        props,
        "mockjdbc.query.events",
        KafkaMockedQueryEventProducer.datasourceKeyResolver()
);
```

Kafka keys are datasource-oriented by default, which preserves per-datasource ordering.

## Multiple datasources

Each `DataSource` gets its own registration and its own datasource id; events from each carry that id in `MockedQuery.datasource_id`:

```java
var usersReg  = JdbcAgentRegistry.register(usersDataSource,  "users-primary",  ..., config);
var ordersReg = JdbcAgentRegistry.register(ordersDataSource, "orders-replica", ..., config);
```

## Tuning async dispatch

Captured events are buffered in a lock-free, array-backed MPMC ring buffer (`MockedQueryRingBuffer`) and drained by sender threads, so the JDBC call path is never blocked on Kafka I/O:

```java
new AsyncDispatchConfig(
    8192,                                    // ring buffer capacity (rounded up to pow2, min 2)
    2,                                       // sender threads
    AsyncDispatchConfig.OverflowStrategy.DROP_OLDEST,
    512                                      // ResultSet row batch size
)
```

Call `registration.close()` to stop the async dispatch threads. Inspect `AsyncMockedQueryEventDispatcher.stats()` (`offered`, `sent`, `dropped`, `failed`, `queued`) to monitor backpressure.

## Running it with the sample app

The `proxy` profile in `mockjdbc-spring-users` demonstrates this end to end:

```bash
mvn spring-boot:run \
  -Dspring-boot.run.profiles=proxy \
  -Dspring-boot.run.jvmArguments="-javaagent:../mockjdbc/mockjdbc-proxy/target/mockjdbc-proxy-0.1.0-SNAPSHOT.jar"
```

Optional environment overrides for the sample app's Kafka producer:

```bash
export MOCKJDBC_PROXY_KAFKA_BOOTSTRAP=localhost:9092
export MOCKJDBC_PROXY_KAFKA_TOPIC=mockjdbc.query.events
```

:::caution Sensitive data
Captured SQL and parameter values are published as-is, with no masking. Do not point the proxy at datasources containing data you cannot share with your Kafka/WireMock infrastructure.
:::

Next: [replay captured events with the mock driver](replaying-with-the-mock-driver.md), or see the full [WireMock bridge guide](../guides/wiremock-bridge.md) for how captured events become replayable stubs.
