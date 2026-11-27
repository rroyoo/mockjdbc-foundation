---
id: supported-jdbc-surface
title: Supported JDBC surface
sidebar_position: 2
---

MockJDBC's JDBC coverage is intentionally partial. This page summarizes current status; the canonical, continuously updated source is [`docs/jdbc-supported-surface.md`](https://github.com/rroyoo/mockjdbc-foundation/blob/main/docs/jdbc-supported-surface.md) in the repository, which is updated in the same change set as any interceptor/handler change.

## Snapshot

| Interface | Status | Notes |
|---|---|---|
| `Connection` | **partial** | Lifecycle, config, client-info, warnings, statement creation, validity, wrappers, metadata, type map, and LOB/array/struct factories are covered. Request/sharding methods and `createSQLXML` are explicitly unsupported. |
| `Statement` | **partial** | Broad execution/batch/config coverage. Wrapper methods (`unwrap`/`isWrapperFor`) and some newer convenience methods are still open. |
| `PreparedStatement` | **partial** | Core execute methods, generated keys, batching, and broad parameter-binding coverage. Full JDBC overload/strict-semantics matrix is not complete. |
| `CallableStatement` | **partial** | OUT parameter registration/retrieval by index is supported. Name-based OUT getters and some advanced types are open. |

Status legend: **implemented** (wired, test-covered) · **partial** (wired, gaps remain) · **open** (not wired yet) · **deferred** (intentionally postponed with explicit rationale).

## Explicitly unsupported (fail fast, not silently)

Unsupported operations throw `SQLFeatureNotSupportedException` rather than returning a default value. For example, on `Connection`:

- `createSQLXML`
- `beginRequest`, `endRequest`, `setShardingKey`, `setShardingKeyIfValid`
- any other `Connection` method not currently wired

This fail-fast contract is intentional: MockJDBC is a debugging tool, and a silently wrong default is worse than a clear exception telling you the operation isn't implemented yet.

## Result-set materialization details

`ResultSetFactory` coerces replayed values to their declared SQL type (`TINYINT`/`SMALLINT`/`INTEGER`/`BIGINT`, `REAL`/`FLOAT`/`DOUBLE`, `NUMERIC`/`DECIMAL`, `BOOLEAN`/`BIT`, `DATE`, `TIME`, `TIMESTAMP`, `BLOB`, `CLOB`). `TIME_WITH_TIMEZONE`/`TIMESTAMP_WITH_TIMEZONE` are rejected with `SQLFeatureNotSupportedException` because the offset isn't transported over the wire. Dates/times backed by protobuf timestamps are interpreted in UTC.

For the full interface-by-interface breakdown (every implemented/partial/open method group), see [`docs/jdbc-supported-surface.md`](https://github.com/rroyoo/mockjdbc-foundation/blob/main/docs/jdbc-supported-surface.md).
