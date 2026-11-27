---
id: known-limitations
title: Known limitations
sidebar_position: 3
---

MockJDBC is a **debugging and testing tool**. Keep these limitations in mind before adopting it.

## Not for production

MockJDBC is intended for debugging, testing, and local/CI development workflows — never production deployment. It has no availability, scaling, or security hardening story for a production data path.

## Partial JDBC coverage

`Connection` is largely closed; `Statement`/`PreparedStatement`/`CallableStatement` support is broad but not complete. See [Supported JDBC surface](supported-jdbc-surface.md). Unsupported operations fail explicitly with `SQLFeatureNotSupportedException` rather than silently returning defaults or stale data.

## Replay is stateless

`commit`/`rollback` are tracked per connection but are **no-ops** — replay does not simulate transactional database state changes. An `UPDATE`/`DELETE` statement returns its recorded JDBC outcome (update count, generated keys), but it does not change what a later `SELECT` returns. Design your tests around *replaying recorded interactions*, not around a stateful in-memory database.

## No sensitive-data masking yet

Captured SQL and parameter values are published by the proxy agent as-is, with no redaction or masking. Do not point the proxy agent at datasources containing data you cannot share with your Kafka/WireMock infrastructure. Masking is tracked as a roadmap item but is not implemented today.

## Docker required for some tests/flows

Kafka and WireMock integration tests use Testcontainers, and the full record→bridge→replay flow (as shown in the [end-to-end tutorial](../guides/end-to-end-tutorial.md)) requires a running Kafka broker and a WireMock gRPC server. There is currently no in-process, Docker-free way to exercise the full pipeline.

## No GUI or CLI yet

MockJDBC is consumed purely through Java APIs, Maven dependencies, and a JDBC URL. There is no standalone CLI or UI for inspecting captured events or managing stub mappings today.

## WireMock bridge packaging constraints

`mockjdbc-wiremock` is a shaded jar built for the WireMock extensions-directory model and deliberately excludes gRPC transport dependencies, which must be supplied by a separately installed WireMock gRPC extension. It is not meant to be added as a general-purpose, self-contained compile dependency. See the [WireMock bridge guide](../guides/wiremock-bridge.md).

If you hit something not listed here, check [Troubleshooting](troubleshooting.md) or [open an issue](https://github.com/rroyoo/mockjdbc-foundation/issues/new/choose).
