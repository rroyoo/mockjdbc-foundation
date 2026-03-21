# Supported JDBC Surface

## 1. Purpose and Scope

This document tracks the currently supported JDBC behavior for:
- `Connection`
- `Statement`
- `PreparedStatement`
- `CallableStatement`

Source of truth:
- `mockjdbc/mockjdbc-mock/src/main/java/io/github/rroyoo/mockjdbc/mock/connection/ConnectionFactory.java`
- `mockjdbc/mockjdbc-mock/src/main/java/io/github/rroyoo/mockjdbc/mock/statement/StatementFactory.java`

## 2. Status Legend

- `implemented`: wired in factory and behavior covered by tests.
- `partial`: wired, but semantics/overloads/tests are not complete.
- `open`: not wired yet, or placeholder behavior remains.
- `deferred`: intentionally postponed with explicit rationale.

## 3. Snapshot Matrix

| Interface | Status | Primary Evidence | Notes |
|---|---|---|---|
| `Connection` | `partial` | `ConnectionFactory`, `ConnectionFactoryTest` | Lifecycle/config/client-info/warnings/statement creation are covered; major JDBC gaps remain |
| `Statement` | `partial` | `StatementFactory`, `StatementFactoryTest` | Broad execution/batch/config coverage; wrappers and newer methods still pending |
| `PreparedStatement` | `partial` | `StatementFactory` prepared interceptors, `StatementFactoryTest` | Many bind/execute methods covered; full contract matrix still pending |
| `CallableStatement` | `partial` | `StatementFactory` callable interceptors, `StatementFactoryTest` | Core OUT param behavior present; full callable surface still pending |

## 4. Interface Surfaces

### 4.1 Connection

**Implemented groups**
- Lifecycle: `close`, `isClosed`
- Transaction basics: `setAutoCommit`, `getAutoCommit`, `commit`, `rollback`
- Savepoint basic hooks: `setSavepoint`, `releaseSavepoint`, `rollback(savepoint)`
- Config/state: read-only, isolation, holdability, catalog, schema, network timeout
- Warnings and client info
- Statement creation delegation: `createStatement`, `prepareStatement`, `prepareCall` overloads

**Partial groups**
- Savepoint semantics are intentionally simplified

**Open groups**
- `nativeSQL`
- Metadata/type map: `getMetaData`, `getTypeMap`, `setTypeMap`
- LOB factories: `createBlob`, `createClob`, `createNClob`, `createSQLXML`
- `createArrayOf`, `createStruct`
- `isValid`, `abort`
- Wrapper methods: `unwrap`, `isWrapperFor`
- Request/sharding methods

### 4.2 Statement

**Implemented groups**
- Query/update/large-update execution families
- Batch and result-state APIs (`executeBatch`, `executeLargeBatch`, `getResultSet`, `getUpdateCount`, `getMoreResults`)
- Lifecycle and warnings
- Config (`maxRows`, `largeMaxRows`, timeout, fetch direction/size, result-set config getters)
- Misc (`setPoolable`, `cancel`, `getConnection`, cursor/escape hooks)

**Partial groups**
- Overloads carrying result-set type/concurrency/holdability are accepted but not fully enforced end-to-end

**Open groups**
- Wrapper methods: `unwrap`, `isWrapperFor`
- Remaining modern convenience methods (for example `enquote*`)

### 4.3 PreparedStatement

**Implemented groups**
- Core execute methods without SQL argument
- Broad parameter binding coverage (primitive, object, temporal, null, stream/blob/clob/nclob)
- Parameter metadata hooks and batching (`addBatch`, `clearParameters`)

**Partial groups**
- Full JDBC overload and strict semantics matrix is not complete

**Open groups**
- Remaining un-intercepted prepared-only methods

### 4.4 CallableStatement

**Implemented groups**
- OUT parameter registration (index/name variants)
- Typed getters by index/name for supported types
- Inherits prepared/query/lifecycle/config behavior from statement pipeline

**Partial groups**
- Callable-specific full-surface completeness (named params, advanced types)

**Open groups**
- Remaining callable methods not currently intercepted

## 5. Maintenance Rules

- Update this file in the same change set as interceptor/handler changes whenever practical.
- Do not move a capability to `implemented` without test evidence.
- Use `deferred` only when rationale is explicit in ADR or issue tracking.
- Group by capability; list individual signatures only when they are exceptions.

## 6. Change Log

- 2026-03-21: Baseline created from current factory wiring and test surface.

