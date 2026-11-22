package io.github.rroyoo.mockjdbc.mock.statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.rroyoo.mockjdbc.mock.ColumnMetadata;
import io.github.rroyoo.mockjdbc.mock.JdbcValue;
import io.github.rroyoo.mockjdbc.mock.MockQueryServiceGrpc;
import io.github.rroyoo.mockjdbc.mock.MockedQuery;
import io.github.rroyoo.mockjdbc.mock.QueryLookupRequest;
import io.github.rroyoo.mockjdbc.mock.Row;
import io.github.rroyoo.mockjdbc.mock.SerializedResultSet;
import io.github.rroyoo.mockjdbc.mock.connection.ConnectionFactory;
import io.github.rroyoo.mockjdbc.mock.driver.MockConfig;
import io.grpc.Server;
import io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder;
import io.grpc.stub.StreamObserver;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ConcurrentConnectionsAndStatementsTest {

    private final ConcurrentMockQueryService service = new ConcurrentMockQueryService(3);
    private Server server;

    @BeforeEach
    void setUp() throws Exception {
        server = NettyServerBuilder.forPort(0).addService(service).build().start();
    }

    @AfterEach
    void tearDown() throws Exception {
        GrpcMockQueryClient.shutdownAll();
        if (server != null) {
            server.shutdownNow();
            server.awaitTermination();
        }
    }

    @Test
    @DisplayName(
            "Given two connections and three statements, when their queries execute concurrently, then connection state and statement results remain isolated")
    void shouldIsolateConcurrentConnectionsAndStatements() throws Exception {
        var executor = Executors.newFixedThreadPool(3);
        var resultsStored = new CountDownLatch(3);
        try (var firstConnection = ConnectionFactory.create(mockConfig(server.getPort()));
                var secondConnection = ConnectionFactory.create(mockConfig(server.getPort()));
                var firstStatement = firstConnection.createStatement();
                var firstPreparedStatement = firstConnection.prepareStatement("SELECT ?");
                var secondPreparedStatement = secondConnection.prepareStatement("SELECT ?")) {
            firstConnection.setAutoCommit(false);
            firstConnection.setCatalog("first_connection");
            secondConnection.setAutoCommit(true);
            secondConnection.setCatalog("second_connection");
            firstPreparedStatement.setString(1, "first_prepared_result");
            secondPreparedStatement.setString(1, "second_prepared_result");

            var statementResult =
                    executor.submit(
                            () ->
                                    executeAndVerify(
                                            firstStatement,
                                            () -> firstStatement.executeQuery("SELECT 1"),
                                            resultsStored));
            var firstPreparedResult =
                    executor.submit(
                            () ->
                                    executeAndVerify(
                                            firstPreparedStatement,
                                            firstPreparedStatement::executeQuery,
                                            resultsStored));
            var secondPreparedResult =
                    executor.submit(
                            () ->
                                    executeAndVerify(
                                            secondPreparedStatement,
                                            secondPreparedStatement::executeQuery,
                                            resultsStored));

            assertEquals("select 1", statementResult.get(10, TimeUnit.SECONDS));
            assertEquals("first_prepared_result", firstPreparedResult.get(10, TimeUnit.SECONDS));
            assertEquals("second_prepared_result", secondPreparedResult.get(10, TimeUnit.SECONDS));
            assertFalse(firstConnection.getAutoCommit());
            assertTrue(secondConnection.getAutoCommit());
            assertEquals("first_connection", firstConnection.getCatalog());
            assertEquals("second_connection", secondConnection.getCatalog());
        } finally {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    private static String executeAndVerify(
            Statement statement, QueryExecution query, CountDownLatch resultsStored)
            throws Exception {
        var resultSet = query.execute();
        resultsStored.countDown();
        if (!resultsStored.await(10, TimeUnit.SECONDS)) {
            throw new SQLException("Concurrent statement results were not all stored");
        }

        try (resultSet) {
            assertSame(resultSet, statement.getResultSet());
            assertTrue(resultSet.next());
            var result = resultSet.getString(1);
            assertFalse(resultSet.next());
            return result;
        }
    }

    private static MockConfig mockConfig(int port) {
        return new MockConfig(new MockConfig.MockServer("127.0.0.1", port), new Properties());
    }

    private static String responseValue(QueryLookupRequest request) {
        return request.getParametersCount() == 0
                ? request.getSql()
                : request.getParameters(0).getValue().getStringVal();
    }

    private static MockedQuery mockedQuery(String value) {
        var resultSet =
                SerializedResultSet.newBuilder()
                        .addMetadata(
                                ColumnMetadata.newBuilder()
                                        .setName("result")
                                        .setLabel("result")
                                        .setSqlType(Types.VARCHAR)
                                        .setTypeName("VARCHAR")
                                        .build())
                        .addRows(
                                Row.newBuilder()
                                        .addValues(
                                                JdbcValue.newBuilder().setStringVal(value).build())
                                        .build())
                        .build();
        return MockedQuery.newBuilder().setResultSet(resultSet).build();
    }

    @FunctionalInterface
    private interface QueryExecution {
        ResultSet execute() throws SQLException;
    }

    private static final class ConcurrentMockQueryService
            extends MockQueryServiceGrpc.MockQueryServiceImplBase {

        private final CountDownLatch requestsReceived;
        private final AtomicBoolean responsesSent = new AtomicBoolean();
        private final ConcurrentHashMap<String, StreamObserver<MockedQuery>> pendingResponses =
                new ConcurrentHashMap<>();

        private ConcurrentMockQueryService(int expectedRequests) {
            requestsReceived = new CountDownLatch(expectedRequests);
        }

        @Override
        public void findMock(
                QueryLookupRequest request, StreamObserver<MockedQuery> responseObserver) {
            var value = responseValue(request);
            pendingResponses.put(value, responseObserver);
            requestsReceived.countDown();
            if (requestsReceived.getCount() == 0 && responsesSent.compareAndSet(false, true)) {
                pendingResponses.forEach(
                        (responseValue, observer) -> {
                            observer.onNext(mockedQuery(responseValue));
                            observer.onCompleted();
                        });
            }
        }
    }
}
