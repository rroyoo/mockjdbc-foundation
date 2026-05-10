package io.github.rroyoo.mockjdbc.wiremock;

import com.github.tomakehurst.wiremock.core.Admin;
import com.github.tomakehurst.wiremock.stubbing.StubMapping;
import io.github.rroyoo.mockjdbc.mock.MockedQuery;
import io.github.rroyoo.mockjdbc.mock.PlainStatement;
import io.github.rroyoo.mockjdbc.mock.QueryExecutionStatus;
import io.github.rroyoo.mockjdbc.mock.SerializedResultSet;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class WireMockMappingRegistrarTest {

    @Test
    @DisplayName("Given mappable event, registrar upserts mapping into WireMock")
    void shouldRegisterMapping() {
        var server = mock(Admin.class);
        var mapper = new MockedQueryStubMapper(List.of(), List.of());
        var registrar = new WireMockMappingRegistrar(server, mapper);

        var event = MockedQuery.newBuilder()
                .setDatasourceId("users-primary")
                .setStatus(QueryExecutionStatus.QUERY_EXECUTION_STATUS_SUCCESS)
                .setSimpleStatement(PlainStatement.newBuilder().setSql("SELECT 1").build())
                .setResultSet(SerializedResultSet.newBuilder().build())
                .build();

        assertTrue(registrar.upsert(event));
        verify(server).removeStubMapping(org.mockito.ArgumentMatchers.any(StubMapping.class));
        verify(server).addStubMapping(org.mockito.ArgumentMatchers.any(StubMapping.class));
    }

    @Test
    @DisplayName("Given filtered event, registrar does nothing")
    void shouldSkipFilteredEvent() {
        var server = mock(Admin.class);
        var mapper = new MockedQueryStubMapper(List.of("orders"), List.of());
        var registrar = new WireMockMappingRegistrar(server, mapper);

        var event = MockedQuery.newBuilder()
                .setDatasourceId("users")
                .setStatus(QueryExecutionStatus.QUERY_EXECUTION_STATUS_SUCCESS)
                .setSimpleStatement(PlainStatement.newBuilder().setSql("SELECT 1").build())
                .setResultSet(SerializedResultSet.newBuilder().build())
                .build();

        assertFalse(registrar.upsert(event));
        verifyNoInteractions(server);
    }
}
