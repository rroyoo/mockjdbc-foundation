# ADR-0002: JDBC proxy capture for multiple datasources and Proto event publication to Kafka

- Status: Accepted
- Date: 2026-03-21
- Owners: `mockjdbc-project-steward`, `jdbc-query-proxy-implementer`

## Context

This ADR was drafted before the capture pipeline existed. The proxy, Kafka publisher, datasource identity, and record/replay integration are now implemented; see the acceptance checklist and current module matrix. The project's scope is debugging and testing, not production deployment.

The project needs a proxy that can be integrated into generic Java JDBC applications, capture SQL activity from multiple datasources, and publish structured events for downstream processing.

## Decision

`mockjdbc-proxy` is an active module with this behavior:

1. Capture JDBC statements across multiple DataSources in the same process.
2. Identify datasource origin for each event with a stable datasource identifier.
3. Publish Proto-encoded capture events to Kafka.
4. Include both query intent and execution outcome in the event payload.
5. Provide usage guides for integrating the proxy in common Java JDBC application styles.

## Required event content

Each emitted Proto event must include at least:
- `datasource_id`: stable identifier for source datasource
- `sql`: executed SQL text
- `parameters`: ordered query parameters when available
- `timing`: start/end or elapsed execution time
- `status`: success or failure
- `result`: result summary (row count/update count where applicable)
- `error`: normalized error payload when execution fails

## Datasource identity strategy

Every DataSource registration requires an explicit datasource ID; the agent does not infer one from a bean name or connection metadata. Callers may use `DatasourceIdentityResolver` themselves to derive an ID from an alias, bean name, or datasource metadata before registration.

## Kafka publication contract

- Serialization: Proto binary payload.
- Topic naming: configurable, environment-specific.
- Keying strategy: datasource-oriented key by default to preserve local ordering per datasource.
- Error handling: asynchronous dispatch uses a bounded queue, configured drop behavior, and observable delivery statistics. Retries and a dead-letter strategy are not implemented; consider them only if debugging use cases require them.

## Integration requirements

Provide official proxy integration guides for:
- plain JDBC (`DriverManager`/manual `DataSource` wiring)
- framework-managed DataSource wrapping
- Spring applications (`JdbcTemplate`, transaction-managed DataSource, JPA-compatible DataSource)
- pooled datasources (HikariCP and equivalent wrappers)

Guides must include:
- bootstrap/configuration steps
- how datasource identity is configured
- Kafka topic/key/serialization settings
- troubleshooting and common failure modes

## Acceptance checklist

- [x] Define and version Proxy Event Proto schema in `mockjdbc-proto`.
- [x] Implement datasource-aware statement capture in `mockjdbc-proxy`.
- [x] Implement datasource identity resolution helpers; require an explicit ID for every DataSource registration.
- [x] Implement Kafka publisher for Proto events.
- [x] Add unit tests for event mapping (query + result + error).
- [x] Add tests for multi-datasource capture and identity consistency.
- [x] Add Kafka publication tests, including the Docker-backed record/replay E2E test in `mockjdbc-spring-users/RecordReplayEndToEndTest`.
- [x] Verify the Docker-backed Kafka producer integration test runs without skips in GitHub Actions (ADR-0005, PR #14).
- [x] Publish integration guides for generic Java JDBC usage.
- [x] Add sample proving two datasources emitting events.
- [x] Capture `CallableStatement` OUT/INOUT parameter values (deferred capture via
      `registerOutParameter`/getter interception; index-based only, see `mockjdbc-proxy/README.md`).
- [x] Replace the async dispatcher's `LinkedBlockingDeque` with a real lock-free, array-backed
      MPMC ring buffer (`MockedQueryRingBuffer`, Vyukov's algorithm). Self-implemented instead of
      adopting LMAX Disruptor: the dispatcher is multi-producer (Disruptor favors
      single-producer), no evidence indicated the buffer itself was the bottleneck, and adding a
      new dependency was not justified. Microbenchmark showed ~60-70% higher raw `publish()`
      throughput than the previous deque; end-to-end throughput under a downstream-I/O-bound
      workload was unchanged, confirming the buffer was not the bottleneck. Revisit Disruptor
      only if future profiling shows the ring buffer itself limits throughput.

## Consequences

### Positive
- Proxy scope becomes explicit and testable.
- Downstream observability/integration gets a stable event contract.
- Multi-datasource behavior is first-class, not incidental.

### Trade-offs
- Additional operational complexity from Kafka and asynchronous dispatch; queue saturation and delivery failures follow the documented drop/statistics behavior.
- Requires careful schema/version governance to avoid breaking consumers.

## Related

- `docs/adr/ADR-0001-project-stewardship-and-gap-closure.md`
- `docs/adr/ADR-0003-mock-driver-scope-and-jdbc-compatibility.md`
