package io.github.rroyoo.mockjdbc.mock.statement;

import io.github.rroyoo.mockjdbc.mock.ColumnMetadata;
import io.github.rroyoo.mockjdbc.mock.JdbcValue;
import io.github.rroyoo.mockjdbc.mock.MockQueryServiceGrpc;
import io.github.rroyoo.mockjdbc.mock.MockedQuery;
import io.github.rroyoo.mockjdbc.mock.ParameterMetadata;
import io.github.rroyoo.mockjdbc.mock.QueryLookupRequest;
import io.github.rroyoo.mockjdbc.mock.Row;
import io.github.rroyoo.mockjdbc.mock.SerializedResultSet;
import io.github.rroyoo.mockjdbc.mock.driver.MockConfig;
import io.grpc.Server;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.sql.Types;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GrpcMockQueryClientTest {

    private final TestMockQueryService service = new TestMockQueryService();
    private Server server;

    @BeforeEach
    void setUp() throws Exception {
        server = NettyServerBuilder.forPort(0)
                .addService(service)
                .build()
                .start();
        service.reset();
    }

    @AfterEach
    void tearDown() throws Exception {
        if (server != null) {
            server.shutdownNow();
            server.awaitTermination();
        }
    }

    @Test
    @DisplayName("Given a reachable grpc server, when findResultSet is called, then it sends SQL and parameters and returns the serialized result")
    void shouldSendLookupRequestAndReturnSerializedResultSet() throws Exception {
        service.responder = request -> mockedQuery(singleRowResultSet());
        var client = new GrpcMockQueryClient(mockConfig(server.getPort()));

        var parameter = ParameterMetadata.newBuilder()
                .setIndex(1)
                .setSqlType(Types.VARCHAR)
                .setTypeName("VARCHAR")
                .setValue(JdbcValue.newBuilder().setStringVal("bob").build())
                .build();

        var resultSet = client.findResultSet("SELECT ?", List.of(parameter));

        assertEquals(1, resultSet.getRowsCount());
        assertNotNull(service.lastRequest.get());
        assertEquals("SELECT ?", service.lastRequest.get().getSql());
        assertEquals(1, service.lastRequest.get().getParametersCount());
        assertEquals("bob", service.lastRequest.get().getParameters(0).getValue().getStringVal());
    }

    @Test
    @DisplayName("Given a grpc error response, when findResultSet is called, then it throws SQLException with grpc cause")
    void shouldWrapGrpcErrorsIntoSqlException() {
        service.failure = Status.UNAVAILABLE.asRuntimeException();
        var client = new GrpcMockQueryClient(mockConfig(server.getPort()));

        var sqlException = assertThrows(SQLException.class, () -> client.findResultSet("SELECT 1", List.of()));

        assertTrue(sqlException.getMessage().contains("SELECT 1"));
        assertNotNull(sqlException.getCause());
        assertSame(StatusRuntimeException.class, sqlException.getCause().getClass());
    }

    private static MockConfig mockConfig(int port) {
        return new MockConfig(new MockConfig.MockServer("127.0.0.1", port), new Properties());
    }

    private static MockedQuery mockedQuery(SerializedResultSet resultSet) {
        return MockedQuery.newBuilder().setResultSet(resultSet).build();
    }

    private static SerializedResultSet singleRowResultSet() {
        return SerializedResultSet.newBuilder()
                .addMetadata(ColumnMetadata.newBuilder()
                        .setName("name")
                        .setLabel("name")
                        .setSqlType(Types.VARCHAR)
                        .setTypeName("VARCHAR")
                        .build())
                .addRows(Row.newBuilder().addValues(JdbcValue.newBuilder().setStringVal("bob").build()).build())
                .build();
    }

    private static final class TestMockQueryService extends MockQueryServiceGrpc.MockQueryServiceImplBase {

        private final AtomicReference<QueryLookupRequest> lastRequest = new AtomicReference<>();
        private volatile Function<QueryLookupRequest, MockedQuery> responder = request -> mockedQuery(SerializedResultSet.getDefaultInstance());
        private volatile StatusRuntimeException failure;

        private void reset() {
            lastRequest.set(null);
            responder = request -> mockedQuery(SerializedResultSet.getDefaultInstance());
            failure = null;
        }

        @Override
        public void findMock(QueryLookupRequest request, StreamObserver<MockedQuery> responseObserver) {
            lastRequest.set(request);
            if (failure != null) {
                responseObserver.onError(failure);
                return;
            }
            responseObserver.onNext(responder.apply(request));
            responseObserver.onCompleted();
        }
    }
}

