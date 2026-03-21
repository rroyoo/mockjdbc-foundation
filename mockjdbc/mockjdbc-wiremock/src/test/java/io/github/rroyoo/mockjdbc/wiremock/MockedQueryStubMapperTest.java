package io.github.rroyoo.mockjdbc.wiremock;

import io.github.rroyoo.mockjdbc.mock.MockedQuery;
import io.github.rroyoo.mockjdbc.mock.PlainStatement;
import io.github.rroyoo.mockjdbc.mock.QueryError;
import io.github.rroyoo.mockjdbc.mock.QueryExecutionStatus;
import io.github.rroyoo.mockjdbc.mock.SerializedResultSet;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MockedQueryStubMapperTest {

    @Test
    @DisplayName("Given a success event, mapper creates deterministic stub mapping")
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
        assertTrue(first.getName().contains("users-primary::select * from users"));
        assertEquals(200, first.getResponse().getStatus());
    }

    @Test
    @DisplayName("Given an error event, mapper creates 500 response stub")
    void shouldCreateErrorMapping() {
        var mapper = new MockedQueryStubMapper(List.of(), List.of());
        var event = MockedQuery.newBuilder()
                .setDatasourceId("users-primary")
                .setStatus(QueryExecutionStatus.QUERY_EXECUTION_STATUS_ERROR)
                .setSimpleStatement(PlainStatement.newBuilder().setSql("SELECT 1").build())
                .setResultSet(SerializedResultSet.newBuilder().build())
                .setError(QueryError.newBuilder().setType("java.lang.IllegalStateException").setMessage("boom").build())
                .build();

        var mapping = mapper.map(event).orElseThrow();
        assertEquals(500, mapping.getResponse().getStatus());
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
}
