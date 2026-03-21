---
name: proto-to-stub-mapping
description: Patterns for transforming MockedQuery protobuf events into WireMock StubMappings with deterministic keys and correct payload encoding.
---
# proto-to-stub-mapping
## When to use
Use this skill when implementing `MockedQueryStubMapper` or any component that converts `MockedQuery` proto messages into WireMock stub definitions.
## Actions
### Extract SQL from MockedQuery
```java
String sql = switch (event.getStatementDefinitionCase()) {
    case SIMPLE_STATEMENT    -> event.getSimpleStatement().getSql();
    case PREPARED_STATEMENT  -> event.getPreparedStatement().getSql();
    case CALLABLE_STATEMENT  -> event.getCallableStatement().getCallSql();
    default -> "";
};
```
### SQL normalization
```java
static String normalize(String sql) {
    if (sql == null) return "";
    return sql.strip()
              .toLowerCase()
              .replaceAll("\\s+", " ")
              .replaceAll(";$", "");
}
```
### Stub key derivation
```java
String stubKey = datasourceId + "::" + normalizedSql;
UUID stubId = UUID.nameUUIDFromBytes(stubKey.getBytes(StandardCharsets.UTF_8));
```
### Success stub payload
- Use `event.getResultSet().toByteArray()` as the response body.
- Set `Content-Type: application/x-protobuf`.
- Status 200.
### Error stub payload
- If `event.getStatus() == QUERY_EXECUTION_STATUS_ERROR`:
  - Status 500.
  - Body: JSON `{"type": "...", "message": "..."}` from `event.getError()`.
### Filtering
- Skip events with blank or empty SQL after normalization.
- Skip events from datasources not in the configured allowlist (if filter is set).
- Skip events where SQL matches a configured deny prefix list.
## Expected output
- A `StubMapping` with a stable UUID, correct request matchers, and correct response payload.
- A normalized SQL string used consistently in key derivation and request matching.
