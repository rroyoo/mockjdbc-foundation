package io.github.rroyoo.mockjdbc.mock.driver;

import io.github.rroyoo.mockjdbc.mock.ColumnMetadata;
import io.github.rroyoo.mockjdbc.mock.JdbcValue;
import io.github.rroyoo.mockjdbc.mock.MockQueryServiceGrpc;
import io.github.rroyoo.mockjdbc.mock.MockedQuery;
import io.github.rroyoo.mockjdbc.mock.PlainStatement;
import io.github.rroyoo.mockjdbc.mock.QueryLookupRequest;
import io.github.rroyoo.mockjdbc.mock.Row;
import io.github.rroyoo.mockjdbc.mock.SerializedResultSet;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.Driver;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class MockDriverGrpcComponentTest {

    private Server grpcServer;
    private MockQueryServiceGrpc.MockQueryServiceImplBase grpcServiceMock;

    @AfterEach
    void tearDown() throws InterruptedException {
        if (grpcServer != null) {
            grpcServer.shutdownNow();
            grpcServer.awaitTermination();
        }
    }

    @Test
    @DisplayName("should execute statement SQL through MockDriver and return ResultSet from mocked gRPC service")
    void shouldExecuteStatementUsingMockedGrpcService() throws Exception {
        AtomicReference<QueryLookupRequest> capturedRequest = new AtomicReference<>();
        startGrpcServerWithMockResponse(mockedQueryWithSingleRow(1L, "Alice"), capturedRequest);

        Driver driver = new MockDriver();
        String url = "jdbc:mock://localhost:" + grpcServer.getPort();

        try (Connection connection = driver.connect(url, new Properties());
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("SELECT id, name FROM users")) {

            assertTrue(resultSet.next());
            assertEquals(1, resultSet.getInt("id"));
            assertEquals("Alice", resultSet.getString("name"));

            QueryLookupRequest request = capturedRequest.get();
            assertEquals("SELECT id, name FROM users", request.getSql());
            assertEquals(0, request.getParametersCount());
            verify(grpcServiceMock, times(1)).findMock(any(), any());
        }
    }

    @Test
    @DisplayName("should execute prepared statement SQL through MockDriver and return ResultSet from mocked gRPC service")
    void shouldExecutePreparedStatementUsingMockedGrpcService() throws Exception {
        AtomicReference<QueryLookupRequest> capturedRequest = new AtomicReference<>();
        startGrpcServerWithMockResponse(mockedQueryWithSingleRow(2L, "Bob"), capturedRequest);

        Driver driver = new MockDriver();
        String url = "jdbc:mock://localhost:" + grpcServer.getPort();

        try (Connection connection = driver.connect(url, new Properties());
             java.sql.PreparedStatement statement = connection.prepareStatement("SELECT id, name FROM users WHERE id = ?");
             ResultSet resultSet = statement.executeQuery()) {

            assertTrue(resultSet.next());
            assertEquals(2, resultSet.getInt("id"));
            assertEquals("Bob", resultSet.getString("name"));

            QueryLookupRequest request = capturedRequest.get();
            assertEquals("SELECT id, name FROM users WHERE id = ?", request.getSql());
            assertEquals(0, request.getParametersCount());
            verify(grpcServiceMock, times(1)).findMock(any(), any());
        }
    }

    private void startGrpcServerWithMockResponse(
        MockedQuery mockedQuery,
        AtomicReference<QueryLookupRequest> capturedRequest
    ) throws Exception {
        grpcServiceMock = mock(MockQueryServiceGrpc.MockQueryServiceImplBase.class, CALLS_REAL_METHODS);

        doAnswer(invocation -> {
            QueryLookupRequest request = invocation.getArgument(0);
            @SuppressWarnings("unchecked")
            StreamObserver<MockedQuery> responseObserver = invocation.getArgument(1);

            capturedRequest.set(request);
            responseObserver.onNext(mockedQuery);
            responseObserver.onCompleted();
            return null;
        }).when(grpcServiceMock).findMock(any(), any());

        grpcServer = ServerBuilder.forPort(0)
            .addService(grpcServiceMock)
            .build()
            .start();
    }

    private MockedQuery mockedQueryWithSingleRow(long id, String name) {
        SerializedResultSet resultSet = SerializedResultSet.newBuilder()
            .addMetadata(ColumnMetadata.newBuilder()
                .setName("id")
                .setLabel("id")
                .setSqlType(java.sql.Types.BIGINT)
                .setTypeName("BIGINT")
                .build())
            .addMetadata(ColumnMetadata.newBuilder()
                .setName("name")
                .setLabel("name")
                .setSqlType(java.sql.Types.VARCHAR)
                .setTypeName("VARCHAR")
                .build())
            .addRows(Row.newBuilder()
                .addValues(JdbcValue.newBuilder().setLongVal(id).build())
                .addValues(JdbcValue.newBuilder().setStringVal(name).build())
                .build())
            .build();

        return MockedQuery.newBuilder()
            .setSimpleStatement(PlainStatement.newBuilder().setSql("ignored").build())
            .setResultSet(resultSet)
            .build();
    }
}

