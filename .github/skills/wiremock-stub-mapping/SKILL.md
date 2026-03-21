---
name: wiremock-stub-mapping
description: Know-how for creating, registering, and managing WireMock stub mappings via the Java API for JDBC query replay.
---
# wiremock-stub-mapping
## When to use
Use this skill when implementing stub creation and registration logic that maps `MockedQuery` proto events to WireMock stub responses.
## Actions
### Stub creation
- Use `WireMock.post(urlEqualTo("/mockjdbc/query"))` as the base request pattern.
- Add `withRequestBody(matchingJsonPath("$.sql", equalTo(normalizedSql)))` for SQL matching.
- Add `withRequestBody(matchingJsonPath("$.datasourceId", equalTo(datasourceId)))` for datasource scoping.
- Set response with `willReturn(aResponse().withStatus(200).withBody(protoBytes).withHeader("Content-Type", "application/x-protobuf"))`.
- Use `withId(UUID.nameUUIDFromBytes(stubKey.getBytes(StandardCharsets.UTF_8)))` for idempotent registration by key.
### Stub registration
- Use `WireMockServer.stubFor(mappingBuilder)` for embedded server registration.
- Use `WireMock.stubFor(mappingBuilder)` for external server registration via admin API.
- For idempotent upsert, use `stubFor` with an explicit `withId`: WireMock replaces existing stubs with the same UUID.
### Stub removal
- Use `WireMockServer.removeStubMapping(stubId)` to clean up stubs by UUID.
- Prefer UUID-based removal over matching-based removal for determinism.
### Error stubs
- For `QUERY_EXECUTION_STATUS_ERROR` events, register a fault stub:
  `willReturn(aResponse().withStatus(500).withBody(errorJson))`.
## SQL normalization contract
- Trim, lowercase, collapse whitespace, strip trailing semicolon.
- Apply normalization before computing the stub UUID to ensure key stability.
## Expected output
- A `StubMapping` that can be registered idempotently for the given `MockedQuery`.
- A stable UUID derived from `datasource_id + "::" + normalized_sql`.
