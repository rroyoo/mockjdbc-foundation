# ADR-0003: Mock JDBC driver scope, behavior contracts, and compatibility roadmap

- Status: Proposed
- Date: 2026-03-21
- Owners: `mockjdbc-project-steward`, `jdbc-interface-implementer`

## Context

`mockjdbc-mock` has significant implementation progress (dynamic `Connection` and statement-family generation via ByteBuddy), but the supported JDBC surface and compatibility boundaries are still evolving.

The project needs a dedicated ADR to define:
- what the mock driver guarantees now
- what remains partial
- how unsupported features are handled
- how incremental compatibility is tracked and verified

## Decision

The mock driver is governed as a compatibility-by-slices product with explicit contracts:

1. Keep deterministic behavior and test-backed semantics for currently supported methods.
2. Maintain explicit status (`implemented`, `partial`, `open`, `deferred`) for JDBC surface areas.
3. Use consistent exception policy for unsupported or invalid operations.
4. Prioritize correctness and debuggability over broad but ambiguous API coverage.

## Current compatibility boundary

The canonical compatibility snapshot is maintained in:
- `docs/jdbc-supported-surface.md`

Implementation sources of truth:
- `mockjdbc/mockjdbc-mock/src/main/java/io/github/rroyoo/mockjdbc/mock/connection/ConnectionFactory.java`
- `mockjdbc/mockjdbc-mock/src/main/java/io/github/rroyoo/mockjdbc/mock/statement/StatementFactory.java`

## Behavioral contracts

### URL and config
- `MockDriver` accepts supported mock JDBC URLs and translates them into `MockConfig`.
- Invalid URLs or invalid config state produce clear SQL exceptions.

### Delegation and state
- Generated JDBC objects delegate behavior to focused handlers (feature-based split).
- Handler-owned state remains per-instance and deterministic.

### Unsupported features
- Use `SQLFeatureNotSupportedException` when functionality is intentionally unsupported.
- Use `SQLException` for contract violations or invalid runtime state transitions.

### Result handling
- Result-set materialization from serialized payloads must remain deterministic.
- Type coverage expansion is incremental and must be documented/tested per slice.

## Verification policy

A feature is only considered `implemented` when:
- factory wiring exists,
- behavior tests exist,
- and the supported-surface document is updated.

## Acceptance checklist

- [ ] Keep `docs/jdbc-supported-surface.md` aligned with implementation.
- [ ] Complete `Connection` high-priority gaps (`nativeSQL`, metadata/type-map, wrappers, validity/abort).
- [ ] Close statement-family wrapper and modern convenience method gaps.
- [ ] Expand result-set type fidelity with dedicated tests.
- [ ] Add integration test for `DriverManager` + SPI discovery path.
- [ ] Ensure exception behavior is consistent with `sql-exception-policy` skill.

## Consequences

### Positive
- Driver scope and guarantees are explicit for contributors and integrators.
- Compatibility progress is measurable and auditable.

### Trade-offs
- Requires ongoing maintenance of status documentation and tests.
- Some JDBC surface may remain intentionally partial for a period.

## Related

- `docs/adr/ADR-0001-project-stewardship-and-gap-closure.md`
- `docs/adr/ADR-0002-jdbc-proxy-capture-and-kafka-event-pipeline.md`
- `docs/jdbc-supported-surface.md`

