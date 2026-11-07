package io.github.rroyoo.mockjdbc.wiremock;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import io.github.rroyoo.mockjdbc.mock.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Types;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MockedQueryStubMapperTest {

    // ── map() ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Given a success event without parameters, mapper creates deterministic stub with correct gRPC URL and headers")
    void shouldCreateDeterministicMapping() {
        var mapper = new MockedQueryStubMapper(List.of(), List.of());
        var event = MockedQuery.newBuilder()
                .setDatasourceId("users-primary")
                .setStatus(QueryExecutionStatus.QUERY_EXECUTION_STATUS_SUCCESS)
                .setSimpleStatement(PlainStatement.newBuilder().setSql(" SELECT  *  FROM users; ").build())
                .setResultSet(SerializedResultSet.newBuilder().build())
                .build();

        var first = mapper.map(event).orElseThrow();
        var second = mapper.map(event).orElseThrow();

        assertEquals(first.getId(), second.getId());
        assertTrue(first.getName().startsWith("users-primary::select * from users"));
        assertEquals(200, first.getResponse().getStatus());
        assertEquals("application/json",
                first.getResponse().getHeaders().getHeader("Content-Type").firstValue());
        assertEquals("OK",
                first.getResponse().getHeaders().getHeader("grpc-status-name").firstValue());
    }

    @Test
    @DisplayName("Given a success event, mapper registers stub at the gRPC FindMock URL")
    void shouldUseGrpcMethodUrl() {
        var mapper = new MockedQueryStubMapper(List.of(), List.of());
        var event = MockedQuery.newBuilder()
                .setDatasourceId("users-primary")
                .setStatus(QueryExecutionStatus.QUERY_EXECUTION_STATUS_SUCCESS)
                .setSimpleStatement(PlainStatement.newBuilder().setSql("SELECT 1").build())
                .setResultSet(SerializedResultSet.newBuilder().build())
                .build();

        var mapping = mapper.map(event).orElseThrow();
        assertEquals(MockedQueryStubMapper.GRPC_METHOD_URL, mapping.getRequest().getUrlPath());
    }

    @Test
    @DisplayName("Given an error event, mapper creates HTTP 200 stub with error JSON body and grpc-status-name OK")
    void shouldCreateErrorMapping() {
        var mapper = new MockedQueryStubMapper(List.of(), List.of());
        var event = MockedQuery.newBuilder()
                .setDatasourceId("users-primary")
                .setStatus(QueryExecutionStatus.QUERY_EXECUTION_STATUS_ERROR)
                .setSimpleStatement(PlainStatement.newBuilder().setSql("SELECT 1").build())
                .setResultSet(SerializedResultSet.newBuilder().build())
                .setError(QueryError.newBuilder()
                        .setType("java.lang.IllegalStateException").setMessage("boom").build())
                .build();

        var mapping = mapper.map(event).orElseThrow();
        assertEquals(200, mapping.getResponse().getStatus());
        assertEquals("OK",
                mapping.getResponse().getHeaders().getHeader("grpc-status-name").firstValue());
        var body = mapping.getResponse().getBody();
        assertTrue(body.contains("\"error\""));
        assertTrue(body.contains("java.lang.IllegalStateException"));
        assertTrue(body.contains("boom"));
    }

    @Test
    @DisplayName("Given SQL differing inside a quoted literal, mapper preserves literal content and creates distinct stubs")
    void shouldPreserveQuotedSqlContentInStubKeysAndMatchers() {
        var mapper = new MockedQueryStubMapper(List.of(), List.of());
        var first = simpleEvent(" SELECT 'Alice  Smith' AS name; ");
        var second = simpleEvent("SELECT 'alice smith' AS name");

        var firstMapping = mapper.map(first).orElseThrow();
        var secondMapping = mapper.map(second).orElseThrow();

        assertNotEquals(firstMapping.getId(), secondMapping.getId());
        assertTrue(firstMapping.getName().contains("select 'Alice  Smith' as name"));
        assertTrue(secondMapping.getName().contains("select 'alice smith' as name"));
        var firstSqlPattern = (com.github.tomakehurst.wiremock.matching.MatchesJsonPathPattern)
                firstMapping.getRequest().getBodyPatterns().get(0);
        var secondSqlPattern = (com.github.tomakehurst.wiremock.matching.MatchesJsonPathPattern)
                secondMapping.getRequest().getBodyPatterns().get(0);
        assertEquals("$.sql", firstSqlPattern.getMatchesJsonPath());
        assertEquals("select 'Alice  Smith' as name", firstSqlPattern.getValuePattern().getValue());
        assertEquals("$.sql", secondSqlPattern.getMatchesJsonPath());
        assertEquals("select 'alice smith' as name", secondSqlPattern.getValuePattern().getValue());
        assertEquals("select 'Alice  Smith' as name", MockedQueryStubMapper.normalizeSql(
                " SELECT 'Alice  Smith' AS name; "));
    }

    @Test
    @DisplayName("Given a prepared statement with one long parameter, mapper adds parameter body pattern and encodes value in stub key")
    void shouldAddParameterBodyPatternForPreparedStatement() {
        var mapper = new MockedQueryStubMapper(List.of(), List.of());
        var event = MockedQuery.newBuilder()
                .setDatasourceId("users-primary")
                .setStatus(QueryExecutionStatus.QUERY_EXECUTION_STATUS_SUCCESS)
                .setPreparedStatement(PreparedStatement.newBuilder()
                        .setSql("SELECT id, name FROM users WHERE id = ?")
                        .addParameters(ParameterMetadata.newBuilder()
                                .setIndex(1)
                                .setValue(JdbcValue.newBuilder().setLongVal(42L).build())
                                .build())
                        .build())
                .setResultSet(SerializedResultSet.newBuilder().build())
                .build();

        var mapping = mapper.map(event).orElseThrow();

        // Stub name / key must encode the parameter value for distinct-key deduplication.
        assertTrue(mapping.getName().contains("::p1=42"), "stub key must include parameter value");

        // Body patterns: one for $.sql, one for the parameter, and one excluding batch lookups.
        var patterns = mapping.getRequest().getBodyPatterns();
        assertEquals(3, patterns.size(), "expected SQL, parameter, and non-batch patterns");
        var patternStrings = patterns.stream()
                .map(b -> ((com.github.tomakehurst.wiremock.matching.MatchesJsonPathPattern) b).getMatchesJsonPath()
                        + "=" + ((com.github.tomakehurst.wiremock.matching.MatchesJsonPathPattern) b).getValuePattern().getValue())
                .toList();
        assertTrue(patternStrings.stream().anyMatch(p -> p.contains("parameters[0].value.longVal")),
                "expected a pattern for parameters[0].value.longVal");
        var batchPattern = (com.github.tomakehurst.wiremock.matching.MatchesJsonPathPattern)
                patterns.get(2);
        assertEquals("$.batchIndex", batchPattern.getMatchesJsonPath());
        assertEquals(WireMock.absent(), batchPattern.getValuePattern());
    }

    @Test
    @DisplayName("Given two captures of the same SQL with different parameter values, mapper generates distinct stubs")
    void shouldGenerateDistinctStubsForDifferentParameterValues() {
        var mapper = new MockedQueryStubMapper(List.of(), List.of());
        var sql = "SELECT id, name FROM users WHERE id = ?";

        var eventId1 = preparedEvent(sql, 1L, "users-primary");
        var eventId2 = preparedEvent(sql, 2L, "users-primary");

        var stub1 = mapper.map(eventId1).orElseThrow();
        var stub2 = mapper.map(eventId2).orElseThrow();

        assertNotEquals(stub1.getId(), stub2.getId(), "stubs for different parameter values must have distinct IDs");
        assertTrue(stub1.getName().contains("::p1=1"));
        assertTrue(stub2.getName().contains("::p1=2"));
    }

    @Test
    @DisplayName("Given two captures of the same SQL and same parameters, mapper generates the same stub ID (upsert)")
    void shouldGenerateSameStubIdForSameParameterValues() {
        var mapper = new MockedQueryStubMapper(List.of(), List.of());
        var first  = mapper.map(preparedEvent("SELECT * FROM t WHERE id = ?", 7L, "ds")).orElseThrow();
        var second = mapper.map(preparedEvent("SELECT * FROM t WHERE id = ?", 7L, "ds")).orElseThrow();
        assertEquals(first.getId(), second.getId());
    }

    @Test
    @DisplayName("Given identical batch entries with different indices, mapper creates distinct index-matched stubs")
    void shouldGenerateDistinctStubsForIdenticalBatchEntries() {
        var mapper = new MockedQueryStubMapper(List.of(), List.of());
        var captured = preparedEvent("DELETE FROM t WHERE id = ?", 7L, "ds");
        var first = mapper.map(captured.toBuilder().setBatchIndex(0).build()).orElseThrow();
        var second = mapper.map(captured.toBuilder().setBatchIndex(1).build()).orElseThrow();

        assertNotEquals(first.getId(), second.getId());
        assertTrue(first.getName().endsWith("::batch=0"));
        assertTrue(second.getName().endsWith("::batch=1"));
        var firstBatchPattern = (com.github.tomakehurst.wiremock.matching.MatchesJsonPathPattern)
                first.getRequest().getBodyPatterns().get(2);
        assertEquals("$.batchIndex", firstBatchPattern.getMatchesJsonPath());
        assertEquals("0", firstBatchPattern.getValuePattern().getValue());
    }

    @Test
    @DisplayName("Given colliding display keys for batch and non-batch captures, mapper still creates distinct UUIDs")
    void shouldAvoidBatchAndNonBatchUuidCollisions() {
        var mapper = new MockedQueryStubMapper(List.of(), List.of());
        var nonBatch = MockedQuery.newBuilder()
                .setDatasourceId("ds")
                .setStatus(QueryExecutionStatus.QUERY_EXECUTION_STATUS_SUCCESS)
                .setPreparedStatement(PreparedStatement.newBuilder()
                        .setSql("SELECT ?")
                        .addParameters(ParameterMetadata.newBuilder()
                                .setIndex(1)
                                .setValue(JdbcValue.newBuilder().setStringVal("x::batch=0").build())
                                .build())
                        .build())
                .setResultSet(SerializedResultSet.newBuilder().build())
                .build();
        var batch = MockedQuery.newBuilder()
                .setDatasourceId("ds")
                .setStatus(QueryExecutionStatus.QUERY_EXECUTION_STATUS_SUCCESS)
                .setPreparedStatement(PreparedStatement.newBuilder()
                        .setSql("SELECT ?")
                        .addParameters(ParameterMetadata.newBuilder()
                                .setIndex(1)
                                .setValue(JdbcValue.newBuilder().setStringVal("x").build())
                                .build())
                        .build())
                .setResultSet(SerializedResultSet.newBuilder().build())
                .setBatchIndex(0)
                .build();

        var nonBatchMapping = mapper.map(nonBatch).orElseThrow();
        var batchMapping = mapper.map(batch).orElseThrow();

        assertEquals(nonBatchMapping.getName(), batchMapping.getName());
        assertNotEquals(nonBatchMapping.getId(), batchMapping.getId());
    }

    @Test
    @DisplayName("Given batch and non-batch stubs, either registration order matches only the intended request")
    void shouldMatchBatchAndNonBatchStubsRegardlessOfRegistrationOrder() throws Exception {
        var mapper = new MockedQueryStubMapper(List.of(), List.of());
        var batch = preparedEvent("SELECT ?", "x", "ds").toBuilder()
                .setBatchIndex(0)
                .setUpdateCount(2)
                .build();
        var nonBatch = preparedEvent("SELECT ?", "x", "ds").toBuilder()
                .setUpdateCount(1)
                .build();
        var batchMapping = mapper.map(batch).orElseThrow();
        var nonBatchMapping = mapper.map(nonBatch).orElseThrow();
        var client = HttpClient.newHttpClient();
        var batchRequest = "{\"sql\":\"select ?\",\"parameters\":[{\"index\":1,"
                + "\"value\":{\"stringVal\":\"x\"}}],\"batchIndex\":0}";
        var nonBatchRequest = "{\"sql\":\"select ?\",\"parameters\":[{\"index\":1,"
                + "\"value\":{\"stringVal\":\"x\"}}]}";

        for (var batchFirst : List.of(true, false)) {
            var server = new WireMockServer(0);
            server.start();
            try {
                if (batchFirst) {
                    server.addStubMapping(batchMapping);
                    server.addStubMapping(nonBatchMapping);
                } else {
                    server.addStubMapping(nonBatchMapping);
                    server.addStubMapping(batchMapping);
                }

                var batchResponse = postJson(client, server, batchRequest);
                var nonBatchResponse = postJson(client, server, nonBatchRequest);

                assertTrue(batchResponse.contains("\"updateCount\":\"2\""));
                assertTrue(nonBatchResponse.contains("\"updateCount\":\"1\""));
            } finally {
                server.stop();
            }
        }
    }

    @Test
    @DisplayName("Given deny prefixes, mapper skips denied SQL")
    void shouldSkipDeniedSql() {
        var mapper = new MockedQueryStubMapper(List.of(), List.of("select 1"));
        var event = MockedQuery.newBuilder()
                .setDatasourceId("users-primary")
                .setStatus(QueryExecutionStatus.QUERY_EXECUTION_STATUS_SUCCESS)
                .setSimpleStatement(PlainStatement.newBuilder().setSql("SELECT 1").build())
                .setResultSet(SerializedResultSet.newBuilder().build())
                .build();

        assertTrue(mapper.map(event).isEmpty());
    }

    @Test
    @DisplayName("Given datasource allowlist, mapper skips unmatched datasource")
    void shouldSkipDatasourceNotInAllowlist() {
        var mapper = new MockedQueryStubMapper(List.of("orders-replica"), List.of());
        var event = MockedQuery.newBuilder()
                .setDatasourceId("users-primary")
                .setStatus(QueryExecutionStatus.QUERY_EXECUTION_STATUS_SUCCESS)
                .setSimpleStatement(PlainStatement.newBuilder().setSql("SELECT 1").build())
                .setResultSet(SerializedResultSet.newBuilder().build())
                .build();

        assertFalse(mapper.map(event).isPresent());
    }

    // ── buildSuccessJsonBody ──────────────────────────────────────────────────

    @Test
    @DisplayName("Given a result set with metadata and rows, buildSuccessJsonBody produces valid protobuf JSON")
    void shouldBuildSuccessJsonBodyWithMetadataAndRows() {
        var rs = SerializedResultSet.newBuilder()
                .addMetadata(ColumnMetadata.newBuilder()
                        .setName("id").setLabel("id").setSqlType(Types.BIGINT).setTypeName("BIGINT").build())
                .addMetadata(ColumnMetadata.newBuilder()
                        .setName("name").setLabel("name").setSqlType(Types.VARCHAR).setTypeName("VARCHAR").build())
                .addRows(Row.newBuilder()
                        .addValues(JdbcValue.newBuilder().setLongVal(1L).build())
                        .addValues(JdbcValue.newBuilder().setStringVal("Alice").build())
                        .build())
                .build();

        var json = MockedQueryStubMapper.buildSuccessJsonBody(rs);

        assertTrue(json.contains("\"resultSet\""));
        assertTrue(json.contains("\"name\":\"id\""));
        assertTrue(json.contains("\"sqlType\":-5"));
        assertTrue(json.contains("\"typeName\":\"BIGINT\""));
        assertTrue(json.contains("\"longVal\":\"1\""));   // int64 → quoted string
        assertTrue(json.contains("\"stringVal\":\"Alice\""));
    }

    @Test
    @DisplayName("Given an empty result set, buildSuccessJsonBody produces valid empty resultSet JSON")
    void shouldBuildSuccessJsonBodyWithEmptyResultSet() {
        var json = MockedQueryStubMapper.buildSuccessJsonBody(SerializedResultSet.newBuilder().build());
        assertEquals("{\"resultSet\":{\"metadata\":[],\"rows\":[]}}", json);
    }

    @Test
    @DisplayName("Given generated keys, buildSuccessJsonBody includes them as a separate result set")
    void shouldBuildSuccessJsonBodyWithGeneratedKeys() {
        var updateResult = SerializedResultSet.newBuilder()
                .addMetadata(ColumnMetadata.newBuilder()
                        .setName("update_count").setLabel("update_count")
                        .setSqlType(Types.BIGINT).setTypeName("BIGINT").build())
                .addRows(Row.newBuilder().addValues(JdbcValue.newBuilder().setLongVal(1).build()).build())
                .build();
        var generatedKeys = SerializedResultSet.newBuilder()
                .addMetadata(ColumnMetadata.newBuilder()
                        .setName("id").setLabel("id").setSqlType(Types.BIGINT).setTypeName("BIGINT").build())
                .addRows(Row.newBuilder().addValues(JdbcValue.newBuilder().setLongVal(42).build()).build())
                .build();

        var json = MockedQueryStubMapper.buildSuccessJsonBody(updateResult, List.of(), generatedKeys);

        assertTrue(json.contains("\"resultSet\":{\"metadata\":[{\"name\":\"update_count\""));
        assertTrue(json.contains("\"generatedKeys\":{\"metadata\":[{\"name\":\"id\""));
        assertTrue(json.contains("\"longVal\":\"42\""));
    }

    @Test
    @DisplayName("Given a callable event with IN and OUT parameters, mapper matches only IN values and returns OUT values in the response")
    void shouldMapCallableEventWithOutParameters() {
        var mapper = new MockedQueryStubMapper(List.of(), List.of());
        var event = MockedQuery.newBuilder()
                .setDatasourceId("users-primary")
                .setStatus(QueryExecutionStatus.QUERY_EXECUTION_STATUS_SUCCESS)
                .setCallableStatement(CallableStatement.newBuilder()
                        .setCallSql("{? = call add_numbers(?, ?)}")
                        .addParameters(callableParameter(1, java.sql.ParameterMetaData.parameterModeOut, 42))
                        .addParameters(callableParameter(2, java.sql.ParameterMetaData.parameterModeIn, 19))
                        .addParameters(callableParameter(3, java.sql.ParameterMetaData.parameterModeIn, 23)))
                .setResultSet(SerializedResultSet.newBuilder().build())
                .build();

        var mapping = mapper.map(event).orElseThrow();

        var patterns = mapping.getRequest().getBodyPatterns().stream()
                .map(b -> ((com.github.tomakehurst.wiremock.matching.MatchesJsonPathPattern) b).getMatchesJsonPath()
                        + "=" + ((com.github.tomakehurst.wiremock.matching.MatchesJsonPathPattern) b).getValuePattern().getValue())
                .toList();
        assertTrue(patterns.stream().anyMatch(p -> p.equals("$.parameters[0].value.longVal=19")));
        assertTrue(patterns.stream().anyMatch(p -> p.equals("$.parameters[1].value.longVal=23")));
        assertTrue(patterns.stream().noneMatch(p -> p.startsWith("$.parameters[2]") || p.endsWith("=42")));
        assertTrue(mapping.getName().endsWith("::p2=19::p3=23"));

        var body = mapping.getResponse().getBody();
        assertTrue(body.contains("\"callableStatement\":{\"parameters\":["));
        assertTrue(body.contains("\"index\":1"));
        assertTrue(body.contains("\"mode\":4"));
        assertTrue(body.contains("\"longVal\":\"42\""));
        assertFalse(body.contains("\"longVal\":\"19\""));
    }

    @Test
    @DisplayName("Given OUT parameters, buildSuccessJsonBody appends callableStatement parameters after the resultSet")
    void shouldAppendOutParametersToSuccessJsonBody() {
        var json = MockedQueryStubMapper.buildSuccessJsonBody(SerializedResultSet.newBuilder().build(),
                List.of(callableParameter(1, java.sql.ParameterMetaData.parameterModeOut, 7)));

        assertEquals("{\"resultSet\":{\"metadata\":[],\"rows\":[]},\"callableStatement\":{\"parameters\":["
                + "{\"index\":1,\"sqlType\":4,\"typeName\":\"Integer\",\"mode\":4,\"value\":{\"longVal\":\"7\"}}]}}", json);
    }

    private static ParameterMetadata callableParameter(int index, int mode, long value) {
        return ParameterMetadata.newBuilder()
                .setIndex(index)
                .setMode(mode)
                .setSqlType(Types.INTEGER)
                .setTypeName("Integer")
                .setValue(JdbcValue.newBuilder().setLongVal(value))
                .build();
    }

    // ── buildErrorJsonBody ────────────────────────────────────────────────────

    @Test
    @DisplayName("Given an error event, buildErrorJsonBody embeds type and message in error field")
    void shouldBuildErrorJsonBody() {
        var event = MockedQuery.newBuilder()
                .setError(QueryError.newBuilder()
                        .setType("java.sql.SQLException")
                        .setMessage("something went wrong")
                        .build())
                .build();

        var json = MockedQueryStubMapper.buildErrorJsonBody(event);
        assertEquals(
                "{\"error\":{\"type\":\"java.sql.SQLException\",\"message\":\"something went wrong\"}}",
                json);
    }

    // ── jdbcValueJson ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("Given various JdbcValue kinds, jdbcValueJson produces correct protobuf JSON encoding")
    void shouldEncodeJdbcValueKinds() {
        assertEquals("{\"longVal\":\"42\"}",
                MockedQueryStubMapper.jdbcValueJson(JdbcValue.newBuilder().setLongVal(42L).build()));
        assertEquals("{\"stringVal\":\"hello\"}",
                MockedQueryStubMapper.jdbcValueJson(JdbcValue.newBuilder().setStringVal("hello").build()));
        assertEquals("{\"boolVal\":true}",
                MockedQueryStubMapper.jdbcValueJson(JdbcValue.newBuilder().setBoolVal(true).build()));
        assertEquals("{\"isNull\":true}",
                MockedQueryStubMapper.jdbcValueJson(JdbcValue.newBuilder().setIsNull(true).build()));
        assertEquals("{\"decimalVal\":\"3.14\"}",
                MockedQueryStubMapper.jdbcValueJson(JdbcValue.newBuilder().setDecimalVal("3.14").build()));
    }

    // ── paramJsonPath / paramExpectedValue ────────────────────────────────────

    @Test
    @DisplayName("Given typed JdbcValues, paramJsonPath returns the correct qualified field path")
    void shouldReturnCorrectParamJsonPath() {
        assertEquals("$.parameters[0].value.longVal",
                MockedQueryStubMapper.paramJsonPath(0, JdbcValue.newBuilder().setLongVal(1L).build()));
        assertEquals("$.parameters[1].value.stringVal",
                MockedQueryStubMapper.paramJsonPath(1, JdbcValue.newBuilder().setStringVal("x").build()));
        assertEquals("$.parameters[0].value.boolVal",
                MockedQueryStubMapper.paramJsonPath(0, JdbcValue.newBuilder().setBoolVal(true).build()));
        assertNull(MockedQueryStubMapper.paramJsonPath(0, JdbcValue.newBuilder().setIsNull(true).build()),
                "IS_NULL should return null (no exact match possible)");
    }

    @Test
    @DisplayName("Given typed JdbcValues, paramExpectedValue returns a stable string representation")
    void shouldReturnCorrectParamExpectedValue() {
        assertEquals("99",    MockedQueryStubMapper.paramExpectedValue(JdbcValue.newBuilder().setLongVal(99L).build()));
        assertEquals("hello", MockedQueryStubMapper.paramExpectedValue(JdbcValue.newBuilder().setStringVal("hello").build()));
        assertEquals("true",  MockedQueryStubMapper.paramExpectedValue(JdbcValue.newBuilder().setBoolVal(true).build()));
        assertEquals("3.14",  MockedQueryStubMapper.paramExpectedValue(JdbcValue.newBuilder().setDecimalVal("3.14").build()));
        assertNull(MockedQueryStubMapper.paramExpectedValue(JdbcValue.newBuilder().setIsNull(true).build()));
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private static MockedQuery preparedEvent(String sql, long paramValue, String datasourceId) {
        return MockedQuery.newBuilder()
                .setDatasourceId(datasourceId)
                .setStatus(QueryExecutionStatus.QUERY_EXECUTION_STATUS_SUCCESS)
                .setPreparedStatement(PreparedStatement.newBuilder()
                        .setSql(sql)
                        .addParameters(ParameterMetadata.newBuilder()
                                .setIndex(1)
                                .setValue(JdbcValue.newBuilder().setLongVal(paramValue).build())
                                .build())
                        .build())
                .setResultSet(SerializedResultSet.newBuilder().build())
                .build();
    }

    private static MockedQuery simpleEvent(String sql) {
        return MockedQuery.newBuilder()
                .setDatasourceId("users-primary")
                .setStatus(QueryExecutionStatus.QUERY_EXECUTION_STATUS_SUCCESS)
                .setSimpleStatement(PlainStatement.newBuilder().setSql(sql).build())
                .setResultSet(SerializedResultSet.newBuilder().build())
                .build();
    }

    private static MockedQuery preparedEvent(String sql, String parameter, String datasourceId) {
        return MockedQuery.newBuilder()
                .setDatasourceId(datasourceId)
                .setStatus(QueryExecutionStatus.QUERY_EXECUTION_STATUS_SUCCESS)
                .setPreparedStatement(PreparedStatement.newBuilder()
                        .setSql(sql)
                        .addParameters(ParameterMetadata.newBuilder()
                                .setIndex(1)
                                .setValue(JdbcValue.newBuilder().setStringVal(parameter).build())
                                .build())
                        .build())
                .setResultSet(SerializedResultSet.newBuilder().build())
                .build();
    }

    private static String postJson(HttpClient client, WireMockServer server, String body) throws Exception {
        var request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + server.port() + MockedQueryStubMapper.GRPC_METHOD_URL))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString()).body();
    }
}
