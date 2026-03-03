# Use Cases

## UC-01: Connect to Mock JDBC

**Intent:** Establish a mock JDBC connection.

**Preconditions**
- JDBC URL matches `MockDriver` accepted format.
- Connection properties include `host` and `port`.

**Flow**
1. Client invokes `DriverManager.getConnection(...)`.
2. `MockDriver` validates URL.
3. `MockConnectionPool` returns `MockConnection`.

**Result**
- Connection object ready for statement lookup operations.

---

## UC-02: Resolve a Plain SQL Mock

**Intent:** Resolve a mock using only SQL text.

**Flow**
1. Client executes a plain statement.
2. Adapter builds `QueryLookupRequest` with SQL.
3. Adapter calls `findMock`.
4. Service returns `MockedQuery`.

**Result**
- Mock definition returned for the SQL.

---

## UC-03: Resolve a Prepared SQL Mock

**Intent:** Resolve a mock using SQL + positional parameters.

**Flow**
1. Client executes a prepared statement.
2. Adapter builds `QueryLookupRequest` with SQL and parameters.
3. Adapter calls `findMock`.
4. Service returns `MockedQuery`.

**Result**
- Parameter-aware mock definition returned.

---

## UC-04: Resolve a Callable SQL Mock

**Intent:** Resolve a mock for callable/procedure style execution.

**Flow**
1. Client executes callable statement.
2. Adapter builds `QueryLookupRequest` from call SQL + params.
3. Adapter calls `findMock`.
4. Service returns `MockedQuery`.

**Result**
- Mock definition returned for callable invocation.

---

## UC-05: Emit Query Execution Event

**Intent:** Publish query execution details through proxy listener interfaces.

**Flow**
1. Query event is mapped to `QueryExecutionEvent`.
2. Listener receives event callback.
3. Logging listener writes event details.

**Result**
- Query execution is observable for audit/logging integrations.

