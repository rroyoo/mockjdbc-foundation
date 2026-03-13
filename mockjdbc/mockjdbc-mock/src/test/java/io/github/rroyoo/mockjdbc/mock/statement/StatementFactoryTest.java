package io.github.rroyoo.mockjdbc.mock.statement;

import io.github.rroyoo.mockjdbc.mock.ColumnMetadata;
import io.github.rroyoo.mockjdbc.mock.JdbcValue;
import io.github.rroyoo.mockjdbc.mock.MockQueryServiceGrpc;
import io.github.rroyoo.mockjdbc.mock.MockedQuery;
import io.github.rroyoo.mockjdbc.mock.QueryLookupRequest;
import io.github.rroyoo.mockjdbc.mock.Row;
import io.github.rroyoo.mockjdbc.mock.SerializedResultSet;
import io.github.rroyoo.mockjdbc.mock.driver.MockConfig;
import io.grpc.Server;
import io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Types;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StatementFactoryTest {

    private final TestMockQueryService service = new TestMockQueryService();
    private Server server;
    private StatementFactory statementFactory;

    @BeforeEach
    void setUp() throws Exception {
        server = NettyServerBuilder.forPort(0)
                .addService(service)
                .build()
                .start();

        statementFactory = new StatementFactory(mockConfig(server.getPort()));
    }

    @AfterEach
    void tearDown() throws Exception {
        if (server != null) {
            server.shutdownNow();
            server.awaitTermination();
        }
    }

    @Test
    @DisplayName("Given a statement, when executeQuery is called, then it materializes the ResultSet returned by grpc")
    void shouldMaterializeResultSetFromGrpcQuery() throws Exception {
        service.responder = request -> mockedQuery(singleColumnResultSet("greeting", "greeting", JdbcValue.newBuilder().setStringVal("hello").build()));

        try (var statement = statementFactory.createStatement();
             var resultSet = statement.executeQuery("SELECT greeting")) {
            assertTrue(resultSet.next());
            assertEquals("hello", resultSet.getString(1));
            assertEquals("hello", resultSet.getString("greeting"));
            assertEquals("hello", resultSet.getObject(1));
            assertFalse(resultSet.next());
        }
    }

    @Test
    @DisplayName("Given a prepared statement, when parameters are bound and executeQuery is called, then it sends them to grpc in the lookup request")
    void shouldSendPreparedStatementParametersToGrpc() throws Exception {
        service.responder = request -> mockedQuery(singleColumnResultSet("name", "name", JdbcValue.newBuilder().setStringVal("bob").build()));

        try (var statement = statementFactory.prepareStatement("SELECT ?")) {
            statement.setString(1, "bob");

            try (var resultSet = statement.executeQuery()) {
                assertTrue(resultSet.next());
                assertEquals("bob", resultSet.getString(1));
            }
        }

        assertNotNull(service.lastRequest.get());
        assertEquals("SELECT ?", service.lastRequest.get().getSql());
        assertEquals(1, service.lastRequest.get().getParametersCount());
        assertEquals(1, service.lastRequest.get().getParameters(0).getIndex());
        assertEquals("bob", service.lastRequest.get().getParameters(0).getValue().getStringVal());
    }

    @Test
    @DisplayName("Given a statement, when it is closed, then isClosed returns true")
    void shouldExposeStatementLifecycleState() throws Exception {
        try (var statement = statementFactory.createStatement()) {
            assertFalse(statement.isClosed());
            statement.close();
            assertTrue(statement.isClosed());
        }
    }

    @Test
    @DisplayName("Given a statement, when execute is called, then it returns true and exposes the current ResultSet")
    void shouldExposeCurrentResultSetAfterStatementExecute() throws Exception {
        service.responder = request -> mockedQuery(singleColumnResultSet("greeting", "greeting", JdbcValue.newBuilder().setStringVal("hello").build()));

        try (var statement = statementFactory.createStatement()) {
            assertTrue(statement.execute("SELECT greeting"));
            assertEquals(-1, statement.getUpdateCount());
            assertNotNull(statement.getResultSet());
            assertTrue(statement.getResultSet().next());
            assertEquals("hello", statement.getResultSet().getString(1));
            assertFalse(statement.getMoreResults());
        }
    }

    @Test
    @DisplayName("Given a prepared statement, when execute is called, then it returns true and exposes the current ResultSet")
    void shouldExposeCurrentResultSetAfterPreparedStatementExecute() throws Exception {
        service.responder = request -> mockedQuery(singleColumnResultSet("name", "name", JdbcValue.newBuilder().setStringVal("bob").build()));

        try (var statement = statementFactory.prepareStatement("SELECT ?")) {
            statement.setString(1, "bob");

            assertTrue(statement.execute());
            assertEquals(-1, statement.getUpdateCount());
            assertNotNull(statement.getResultSet());
            assertTrue(statement.getResultSet().next());
            assertEquals("bob", statement.getResultSet().getString(1));
            assertFalse(statement.getMoreResults());
        }
    }

    @Test
    @DisplayName("Given a statement, when executeUpdate is called, then it returns update count and clears current ResultSet")
    void shouldReturnUpdateCountAndClearResultSetAfterStatementExecuteUpdate() throws Exception {
        service.responder = request -> mockedQuery(twoRowResultSet("id", "id"));

        try (var statement = statementFactory.createStatement()) {
            var updatedRows = statement.executeUpdate("UPDATE users SET active=true");
            assertEquals(2, updatedRows);
            assertEquals(2, statement.getUpdateCount());
            assertNull(statement.getResultSet());
            assertFalse(statement.getMoreResults());
        }
    }

    @Test
    @DisplayName("Given a prepared statement, when executeUpdate is called, then it returns update count and clears current ResultSet")
    void shouldReturnUpdateCountAndClearResultSetAfterPreparedStatementExecuteUpdate() throws Exception {
        service.responder = request -> mockedQuery(twoRowResultSet("id", "id"));

        try (var statement = statementFactory.prepareStatement("UPDATE users SET active=? WHERE id=?")) {
            statement.setBoolean(1, true);
            statement.setInt(2, 10);

            var updatedRows = statement.executeUpdate();
            assertEquals(2, updatedRows);
            assertEquals(2, statement.getUpdateCount());
            assertNull(statement.getResultSet());
            assertFalse(statement.getMoreResults());
        }
    }

    private static MockConfig mockConfig(int port) {
        return new MockConfig(new MockConfig.MockServer("127.0.0.1", port), new Properties());
    }

    private static MockedQuery mockedQuery(SerializedResultSet resultSet) {
        return MockedQuery.newBuilder()
                .setResultSet(resultSet)
                .build();
    }

    private static SerializedResultSet singleColumnResultSet(String name, String label, JdbcValue value) {
        return SerializedResultSet.newBuilder()
                .addMetadata(ColumnMetadata.newBuilder()
                        .setName(name)
                        .setLabel(label)
                        .setSqlType(Types.VARCHAR)
                        .setTypeName("VARCHAR")
                        .build())
                .addRows(Row.newBuilder().addValues(value).build())
                .build();
    }

    private static SerializedResultSet twoRowResultSet(String name, String label) {
        return SerializedResultSet.newBuilder()
                .addMetadata(ColumnMetadata.newBuilder()
                        .setName(name)
                        .setLabel(label)
                        .setSqlType(Types.INTEGER)
                        .setTypeName("INTEGER")
                        .build())
                .addRows(Row.newBuilder().addValues(JdbcValue.newBuilder().setLongVal(1).build()).build())
                .addRows(Row.newBuilder().addValues(JdbcValue.newBuilder().setLongVal(2).build()).build())
                .build();
    }

    private static final class TestMockQueryService extends MockQueryServiceGrpc.MockQueryServiceImplBase {

        private final AtomicReference<QueryLookupRequest> lastRequest = new AtomicReference<>();
        private volatile Function<QueryLookupRequest, MockedQuery> responder = request -> mockedQuery(SerializedResultSet.getDefaultInstance());

        @Override
        public void findMock(QueryLookupRequest request, StreamObserver<MockedQuery> responseObserver) {
            lastRequest.set(request);
            responseObserver.onNext(responder.apply(request));
            responseObserver.onCompleted();
        }
    }
}
