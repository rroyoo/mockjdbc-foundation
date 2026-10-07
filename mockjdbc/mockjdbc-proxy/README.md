# mockjdbc-proxy

JDBC capture module implemented as a **Java agent** — no DataSource wrapping required.

## How it works

The agent installs ByteBuddy instrumentation on `DataSource.getConnection()` at JVM startup (or
dynamically via attachment). Every `Connection` returned by a registered DataSource is transparently
wrapped so that all statement executions — `Statement`, `PreparedStatement`, `CallableStatement` —
are captured and published as protobuf `MockedQuery` events.

```
DataSource.getConnection()
   └─ CapturingConnectionInvocationHandler  (JDK proxy)
        ├─ createStatement()   → CapturingStatementInvocationHandler
        ├─ prepareStatement()  → CapturingPreparedStatementInvocationHandler
        └─ prepareCall()       → CapturingPreparedStatementInvocationHandler (CallableStatement)
```

Each execution emits:
- A local `JdbcQueryInterceptedEvent` (in-process observer)
- An async `MockedQuery` protobuf event via the configured `MockedQueryEventProducer` (e.g., Kafka)

## Quick start

### 1. Add as `-javaagent`

```bash
java -javaagent:/path/to/mockjdbc-proxy.jar -jar my-app.jar
```

### 2. Register DataSources

```java
import io.github.rroyoo.mockjdbc.proxy.*;

DataSource usersDataSource = ...;

JdbcCaptureRegistration registration = JdbcAgentRegistry.register(
        usersDataSource,
        "users-primary",          // logical datasource id
        event -> {},              // local JdbcQueryInterceptedEvent consumer
        kafkaProducer,            // MockedQueryEventProducer (e.g. KafkaMockedQueryEventProducer)
        AsyncDispatchConfig.defaults()
);

// Use usersDataSource normally — all JDBC executions are captured automatically
```

### 3. Inspect captured events

```java
List<JdbcQueryInterceptedEvent> events = registration.events();
registration.clear();             // clear in-memory buffer
registration.close();             // stop async dispatch threads
```

## Programmatic agent installation (tests / frameworks)

```java
import net.bytebuddy.agent.ByteBuddyAgent;
import io.github.rroyoo.mockjdbc.proxy.JdbcCaptureAgent;

Instrumentation inst = ByteBuddyAgent.install();
JdbcCaptureAgent.install(inst);
```

## Multi-datasource registration

Each DataSource gets its own `JdbcCaptureRegistration` with a distinct datasource id:

```java
var usersReg  = JdbcAgentRegistry.register(usersDataSource,  "users-primary",  ..., config);
var ordersReg = JdbcAgentRegistry.register(ordersDataSource, "orders-replica", ..., config);

// Use usersDataSource and ordersDataSource normally
// Events from each carry their own datasource id in MockedQuery.datasource_id
```

## Kafka producer integration

```java
Properties props = new Properties();
props.setProperty("bootstrap.servers", "localhost:9092");
props.setProperty("acks", "all");

MockedQueryEventProducer kafkaProducer = KafkaMockedQueryEventProducer.create(
        props,
        "mockjdbc.query.events",
        KafkaMockedQueryEventProducer.datasourceKeyResolver()
);

JdbcAgentRegistry.register(dataSource, "users-primary", event -> {}, kafkaProducer, config);
```

## Captured event fields (MockedQuery protobuf)

| Field | Description |
|---|---|
| `datasource_id` | logical datasource identifier |
| `elapsed_time_millis` | total execution + result set iteration time |
| `status` | `SUCCESS` or `ERROR` |
| `row_count` | rows returned (SELECT queries) |
| `update_count` | rows affected (UPDATE/INSERT/DELETE) |
| `event_id` | random UUID per event |
| `observed_at` | wall-clock timestamp |
| `result_set` | serialized columns + rows (or single `update_count` row) |
| `simple_statement` / `prepared_statement` / `callable_statement` | statement type with SQL and parameters |

## CallableStatement OUT/INOUT parameter capture

`CapturingPreparedStatementInvocationHandler` tracks `registerOutParameter(int, ...)` calls and
intercepts the matching `getXxx(int)` getters after execution. Because OUT values are only known
once the caller retrieves them (strictly after `execute*`), publishing is deferred: the capture
event is held as a pending record and flushed on the statement's next lifecycle call
(`set*`/`execute*`/`registerOutParameter`/`close`), merging IN parameter values with any OUT/INOUT
values retrieved in between.

Known limitations: only index-based `registerOutParameter(int, ...)` is supported (not the
named-parameter overload), and deferred OUT capture applies to `executeUpdate()` and the
update-count branch of `execute()` — `executeQuery()`/`executeLargeUpdate()` and the ResultSet
branch of `execute()` do not defer OUT capture.

## Async dispatch tuning

`AsyncMockedQueryEventDispatcher` buffers events in `MockedQueryRingBuffer`, a lock-free
array-backed multi-producer/multi-consumer ring buffer (Vyukov's bounded MPMC algorithm, no
external dependency). `capacity` is rounded up to the next power of two and floored at **2**
(capacity 1 is not valid for this algorithm — the producer's free-slot check and the consumer's
ready-to-consume check would collide on the same sequence value). The sender loop drains via
non-blocking `tryPoll()` with a short `LockSupport.parkNanos` backoff when idle, instead of
blocking on the queue.

`AsyncMockedQueryEventDispatcher.stats()` exposes: `offered`, `sent`, `dropped`, `failed`, `queued`.

Tune `AsyncDispatchConfig` for throughput / latency / memory trade-offs:

```java
new AsyncDispatchConfig(
    8192,                                    // ring buffer capacity (rounded up to pow2, min 2)
    2,                                       // sender threads
    AsyncDispatchConfig.OverflowStrategy.DROP_OLDEST,
    512                                      // ResultSet row batch size
)
```

A microbenchmark (`publish()`-only, no downstream I/O) measured ~9-10M ops/sec for the ring
buffer versus ~5-6M ops/sec for the previous `LinkedBlockingDeque`-based implementation
(~60-70% higher raw enqueue throughput). Under a downstream-latency-bound workload the
end-to-end sent rate is unchanged, since the bottleneck is the producer's I/O, not the buffer —
this was expected and is why LMAX Disruptor was not adopted (see ADR-0002).

## Memory strategy for ResultSet capture

Rows are captured in batches (`rowBatchSize`) during normal iteration. Each batch emits one
`MockedQuery` event so memory is bounded. Large result sets do not accumulate in RAM.

## Datasource identity strategy

Registration always requires an explicit id — there is no fallback fingerprinting at the agent
level. Use `DatasourceIdentityResolver.resolve(alias, beanName, dataSource)` if you need
the hash-based fallback from a connection metadata fingerprint.

## Verify

```bash
mvn -pl mockjdbc-proxy test
```
