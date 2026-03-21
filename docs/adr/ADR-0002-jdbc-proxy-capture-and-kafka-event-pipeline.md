# ADR-0002: JDBC proxy capture for multiple datasources and Proto event publication to Kafka

- Status: Proposed
- Date: 2026-03-21
- Owners: `mockjdbc-project-steward`, `jdbc-query-proxy-implementer`

## Context

`mockjdbc-proxy` is currently present in the reactor but its product scope has not been fully implemented or documented as an executable contract.

The project needs a proxy that can be integrated into generic Java JDBC applications, capture SQL activity from multiple datasources, and publish structured events for downstream processing.

## Decision

`mockjdbc-proxy` will evolve to an active module with this target behavior:

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

Datasource identification is resolved in this precedence order:
1. Explicit configured datasource name/alias.
2. Framework-provided bean name (when available).
3. Deterministic fallback fingerprint from datasource metadata (URL + user + driver, sanitized/hash).

The same datasource must always generate the same logical `datasource_id` in a given deployment.

## Kafka publication contract

- Serialization: Proto binary payload.
- Topic naming: configurable, environment-specific.
- Keying strategy: datasource-oriented key by default to preserve local ordering per datasource.
- Error handling: retry with bounded policy and explicit dead-letter strategy for non-recoverable failures.

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
- [x] Implement datasource identity resolver with precedence policy.
- [x] Implement Kafka publisher for Proto events.
- [x] Add unit tests for event mapping (query + result + error).
- [x] Add tests for multi-datasource capture and identity consistency.
- [ ] Add Kafka publication tests (unit done; integration test implemented and executes when Docker is available).
- [x] Publish integration guides for generic Java JDBC usage.
- [x] Add sample proving two datasources emitting events.

## Consequences

### Positive
- Proxy scope becomes explicit and testable.
- Downstream observability/integration gets a stable event contract.
- Multi-datasource behavior is first-class, not incidental.

### Trade-offs
- Additional operational complexity (Kafka, retries, dead-letter handling).
- Requires careful schema/version governance to avoid breaking consumers.

## Related

- `docs/adr/ADR-0001-project-stewardship-and-gap-closure.md`
- `docs/adr/ADR-0003-mock-driver-scope-and-jdbc-compatibility.md`
