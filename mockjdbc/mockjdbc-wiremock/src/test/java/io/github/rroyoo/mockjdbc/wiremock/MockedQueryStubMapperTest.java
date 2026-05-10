package io.github.rroyoo.mockjdbc.wiremock;

import io.github.rroyoo.mockjdbc.mock.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

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

        // Body patterns: one for $.sql, one for the parameter.
        var patterns = mapping.getRequest().getBodyPatterns();
        assertEquals(2, patterns.size(), "expected $.sql pattern + one parameter pattern");
        var patternStrings = patterns.stream().map(Object::toString).toList();
        assertTrue(patternStrings.stream().anyMatch(p -> p.contains("parameters[0].value.longVal")),
                "expected a pattern for parameters[0].value.longVal");
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
}
