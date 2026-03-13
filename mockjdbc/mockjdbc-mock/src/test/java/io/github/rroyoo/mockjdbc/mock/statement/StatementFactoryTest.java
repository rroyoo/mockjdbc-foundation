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

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Time;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
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

        service.reset();
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

    @Test
    @DisplayName("Given a statement, when executeLargeUpdate is called, then it returns large update count and clears current ResultSet")
    void shouldReturnLargeUpdateCountAndClearResultSetAfterStatementExecuteLargeUpdate() throws Exception {
        service.responder = request -> mockedQuery(twoRowResultSet("id", "id"));

        try (var statement = statementFactory.createStatement()) {
            var updatedRows = statement.executeLargeUpdate("UPDATE users SET active=true");
            assertEquals(2L, updatedRows);
            assertEquals(2L, statement.getLargeUpdateCount());
            assertEquals(2, statement.getUpdateCount());
            assertNull(statement.getResultSet());
            assertFalse(statement.getMoreResults());
        }
    }

    @Test
    @DisplayName("Given a prepared statement, when executeLargeUpdate is called, then it returns large update count and clears current ResultSet")
    void shouldReturnLargeUpdateCountAndClearResultSetAfterPreparedStatementExecuteLargeUpdate() throws Exception {
        service.responder = request -> mockedQuery(twoRowResultSet("id", "id"));

        try (var statement = statementFactory.prepareStatement("UPDATE users SET active=? WHERE id=?")) {
            statement.setBoolean(1, true);
            statement.setInt(2, 10);

            var updatedRows = statement.executeLargeUpdate();
            assertEquals(2L, updatedRows);
            assertEquals(2L, statement.getLargeUpdateCount());
            assertEquals(2, statement.getUpdateCount());
            assertNull(statement.getResultSet());
            assertFalse(statement.getMoreResults());
        }
    }

    @Test
    @DisplayName("Given a statement with current ResultSet, when getMoreResults CLOSE_CURRENT_RESULT is called, then it returns false and clears current ResultSet")
    void shouldClearCurrentResultSetWhenGetMoreResultsCloseCurrentResultIsCalled() throws Exception {
        service.responder = request -> mockedQuery(singleColumnResultSet("greeting", "greeting", JdbcValue.newBuilder().setStringVal("hello").build()));

        try (var statement = statementFactory.createStatement()) {
            statement.execute("SELECT greeting");
            assertNotNull(statement.getResultSet());

            assertFalse(statement.getMoreResults(java.sql.Statement.CLOSE_CURRENT_RESULT));
            assertNull(statement.getResultSet());
        }
    }

    @Test
    @DisplayName("Given a statement with current ResultSet, when getMoreResults KEEP_CURRENT_RESULT is called, then it keeps current ResultSet")
    void shouldKeepCurrentResultSetWhenGetMoreResultsKeepCurrentResultIsCalled() throws Exception {
        service.responder = request -> mockedQuery(singleColumnResultSet("greeting", "greeting", JdbcValue.newBuilder().setStringVal("hello").build()));

        try (var statement = statementFactory.createStatement()) {
            statement.execute("SELECT greeting");
            assertNotNull(statement.getResultSet());

            assertFalse(statement.getMoreResults(java.sql.Statement.KEEP_CURRENT_RESULT));
            assertNotNull(statement.getResultSet());
            assertTrue(statement.getResultSet().next());
            assertEquals("hello", statement.getResultSet().getString(1));
        }
    }

    @Test
    @DisplayName("Given a new statement, when closeOnCompletion is called, then isCloseOnCompletion returns true")
    void shouldEnableCloseOnCompletionFlag() throws Exception {
        try (var statement = statementFactory.createStatement()) {
            assertFalse(statement.isCloseOnCompletion());
            statement.closeOnCompletion();
            assertTrue(statement.isCloseOnCompletion());
        }
    }

    @Test
    @DisplayName("Given a new statement, when reading config defaults, then JDBC-like defaults are returned")
    void shouldReturnStatementConfigDefaults() throws Exception {
        try (var statement = statementFactory.createStatement()) {
            assertEquals(0, statement.getMaxRows());
            assertEquals(0L, statement.getLargeMaxRows());
            assertEquals(0, statement.getQueryTimeout());
            assertEquals(0, statement.getFetchSize());
            assertEquals(java.sql.ResultSet.FETCH_FORWARD, statement.getFetchDirection());
            assertEquals(java.sql.ResultSet.TYPE_FORWARD_ONLY, statement.getResultSetType());
            assertEquals(java.sql.ResultSet.CONCUR_READ_ONLY, statement.getResultSetConcurrency());
            assertEquals(java.sql.ResultSet.CLOSE_CURSORS_AT_COMMIT, statement.getResultSetHoldability());
        }
    }

    @Test
    @DisplayName("Given a statement, when setting config values, then getters return updated values")
    void shouldStoreAndReturnStatementConfigValues() throws Exception {
        try (var statement = statementFactory.createStatement()) {
            statement.setMaxRows(50);
            statement.setLargeMaxRows(500L);
            statement.setQueryTimeout(12);
            statement.setFetchSize(25);
            statement.setFetchDirection(java.sql.ResultSet.FETCH_REVERSE);

            assertEquals(50, statement.getMaxRows());
            assertEquals(500L, statement.getLargeMaxRows());
            assertEquals(12, statement.getQueryTimeout());
            assertEquals(25, statement.getFetchSize());
            assertEquals(java.sql.ResultSet.FETCH_REVERSE, statement.getFetchDirection());
        }
    }

    @Test
    @DisplayName("Given a prepared statement, when setting config values, then getters return updated values")
    void shouldStoreAndReturnPreparedStatementConfigValues() throws Exception {
        try (var statement = statementFactory.prepareStatement("SELECT ?")) {
            statement.setMaxRows(7);
            statement.setLargeMaxRows(70L);
            statement.setQueryTimeout(3);
            statement.setFetchSize(5);
            statement.setFetchDirection(java.sql.ResultSet.FETCH_UNKNOWN);

            assertEquals(7, statement.getMaxRows());
            assertEquals(70L, statement.getLargeMaxRows());
            assertEquals(3, statement.getQueryTimeout());
            assertEquals(5, statement.getFetchSize());
            assertEquals(java.sql.ResultSet.FETCH_UNKNOWN, statement.getFetchDirection());
        }
    }

    @Test
    @DisplayName("Given statement executeUpdate with RETURN_GENERATED_KEYS, when getGeneratedKeys is called, then it returns keys from grpc result")
    void shouldReturnGeneratedKeysAfterStatementExecuteUpdateWithGeneratedKeysFlag() throws Exception {
        service.responder = request -> mockedQuery(twoRowResultSet("id", "id"));

        try (var statement = statementFactory.createStatement()) {
            statement.executeUpdate("UPDATE users SET active=true", java.sql.Statement.RETURN_GENERATED_KEYS);

            try (var generatedKeys = statement.getGeneratedKeys()) {
                assertTrue(generatedKeys.next());
                assertEquals(1L, generatedKeys.getObject(1));
            }
        }
    }

    @Test
    @DisplayName("Given prepared statement created with generated keys mode, when executeUpdate is called, then getGeneratedKeys returns keys")
    void shouldReturnGeneratedKeysAfterPreparedStatementExecuteUpdateWhenConfigured() throws Exception {
        service.responder = request -> mockedQuery(twoRowResultSet("id", "id"));

        try (var statement = statementFactory.prepareStatement("UPDATE users SET active=?", java.sql.Statement.RETURN_GENERATED_KEYS)) {
            statement.setBoolean(1, true);
            statement.executeUpdate();

            try (var generatedKeys = statement.getGeneratedKeys()) {
                assertTrue(generatedKeys.next());
                assertEquals(1L, generatedKeys.getObject(1));
            }
        }
    }

    @Test
    @DisplayName("Given a statement with batched SQLs, when executeBatch is called, then it returns deterministic update counts")
    void shouldExecuteStatementBatchAndReturnCounts() throws Exception {
        service.responder = request -> mockedQuery(twoRowResultSet("id", "id"));

        try (var statement = statementFactory.createStatement()) {
            statement.addBatch("UPDATE users SET active=true WHERE id=1");
            statement.addBatch("UPDATE users SET active=true WHERE id=2");

            var result = statement.executeBatch();

            assertEquals(2, result.length);
            assertEquals(2, result[0]);
            assertEquals(2, result[1]);
            assertEquals(2, service.callCount.get());
            assertEquals(-1, statement.getUpdateCount());
        }
    }

    @Test
    @DisplayName("Given a prepared statement with batched parameter sets, when executeBatch is called, then it returns deterministic update counts")
    void shouldExecutePreparedStatementBatchAndReturnCounts() throws Exception {
        service.responder = request -> mockedQuery(twoRowResultSet("id", "id"));

        try (var statement = statementFactory.prepareStatement("UPDATE users SET active=? WHERE id=?")) {
            statement.setBoolean(1, true);
            statement.setInt(2, 1);
            statement.addBatch();

            statement.setBoolean(1, false);
            statement.setInt(2, 2);
            statement.addBatch();

            var result = statement.executeBatch();

            assertEquals(2, result.length);
            assertEquals(2, result[0]);
            assertEquals(2, result[1]);
            assertEquals(2, service.callCount.get());
            assertEquals(-1, statement.getUpdateCount());
        }
    }

    @Test
    @DisplayName("Given a new statement, when isPoolable is called, then it returns true by default")
    void shouldReturnTrueByDefaultForPoolable() throws Exception {
        try (var statement = statementFactory.createStatement()) {
            assertTrue(statement.isPoolable());
        }
    }

    @Test
    @DisplayName("Given a statement, when setPoolable false is called, then isPoolable returns false")
    void shouldStorePoolableFlag() throws Exception {
        try (var statement = statementFactory.createStatement()) {
            statement.setPoolable(false);
            assertFalse(statement.isPoolable());

            statement.setPoolable(true);
            assertTrue(statement.isPoolable());
        }
    }

    @Test
    @DisplayName("Given a statement, when setEscapeProcessing and setCursorName are called, then they do not throw")
    void shouldAcceptEscapeProcessingAndCursorNameWithoutThrowing() throws Exception {
        try (var statement = statementFactory.createStatement()) {
            assertDoesNotThrow(() -> statement.setEscapeProcessing(false));
            assertDoesNotThrow(() -> statement.setEscapeProcessing(true));
            assertDoesNotThrow(() -> statement.setCursorName("my_cursor"));
        }
    }

    @Test
    @DisplayName("Given a statement, when cancel is called, then it does not throw")
    void shouldCancelWithoutThrowing() throws Exception {
        try (var statement = statementFactory.createStatement()) {
            assertDoesNotThrow(statement::cancel);
        }
    }

    @Test
    @DisplayName("Given a statement, when getConnection is called, then it returns a non-null open connection")
    void shouldReturnOpenConnectionFromStatement() throws Exception {
        service.responder = request -> mockedQuery(SerializedResultSet.getDefaultInstance());

        try (var statement = statementFactory.createStatement();
             var connection = statement.getConnection()) {
            assertNotNull(connection);
            assertFalse(connection.isClosed());
        }
    }

    @Test
    @DisplayName("Given a prepared statement, when numeric type setters are called, then they are sent in the lookup request")
    void shouldSendNumericTypeParametersToGrpc() throws Exception {
        service.responder = request -> mockedQuery(SerializedResultSet.getDefaultInstance());

        try (var statement = statementFactory.prepareStatement("SELECT ?,?,?,?,?")) {
            statement.setDouble(1, 3.14);
            statement.setFloat(2, 2.72f);
            statement.setShort(3, (short) 42);
            statement.setByte(4, (byte) 7);
            statement.setBigDecimal(5, new BigDecimal("123.456"));
            statement.executeUpdate();
        }

        var req = service.lastRequest.get();
        assertNotNull(req);
        assertEquals(5, req.getParametersCount());
        assertEquals(3.14, req.getParameters(0).getValue().getDoubleVal(), 0.001);
        assertEquals(2.72f, (float) req.getParameters(1).getValue().getDoubleVal(), 0.001f);
        assertEquals(42L, req.getParameters(2).getValue().getLongVal());
        assertEquals(7L, req.getParameters(3).getValue().getLongVal());
        assertEquals("123.456", req.getParameters(4).getValue().getDecimalVal());
    }

    @Test
    @DisplayName("Given a prepared statement, when temporal setters are called, then they are sent as strings in the lookup request")
    void shouldSendTemporalParametersToGrpc() throws Exception {
        service.responder = request -> mockedQuery(SerializedResultSet.getDefaultInstance());

        var date = Date.valueOf("2026-03-13");
        var time = Time.valueOf("12:00:00");
        var timestamp = Timestamp.valueOf("2026-03-13 12:00:00");

        try (var statement = statementFactory.prepareStatement("SELECT ?,?,?")) {
            statement.setDate(1, date);
            statement.setTime(2, time);
            statement.setTimestamp(3, timestamp);
            statement.executeUpdate();
        }

        var req = service.lastRequest.get();
        assertNotNull(req);
        assertEquals(3, req.getParametersCount());
        assertEquals(date.toString(), req.getParameters(0).getValue().getStringVal());
        assertEquals(time.toString(), req.getParameters(1).getValue().getStringVal());
        assertEquals(timestamp.toString(), req.getParameters(2).getValue().getStringVal());
    }

    @Test
    @DisplayName("Given a prepared statement, when setBytes is called with a non-null value, then it is sent in the lookup request")
    void shouldSendBytesParameterToGrpc() throws Exception {
        service.responder = request -> mockedQuery(SerializedResultSet.getDefaultInstance());

        var data = new byte[]{1, 2, 3, 4};

        try (var statement = statementFactory.prepareStatement("SELECT ?")) {
            statement.setBytes(1, data);
            statement.executeUpdate();
        }

        var req = service.lastRequest.get();
        assertNotNull(req);
        assertEquals(1, req.getParametersCount());
        assertEquals(com.google.protobuf.ByteString.copyFrom(data), req.getParameters(0).getValue().getBytesVal());
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
        private final AtomicInteger callCount = new AtomicInteger(0);
        private volatile Function<QueryLookupRequest, MockedQuery> responder = request -> mockedQuery(SerializedResultSet.getDefaultInstance());

        void reset() {
            lastRequest.set(null);
            callCount.set(0);
            responder = request -> mockedQuery(SerializedResultSet.getDefaultInstance());
        }

        @Override
        public void findMock(QueryLookupRequest request, StreamObserver<MockedQuery> responseObserver) {
            callCount.incrementAndGet();
            lastRequest.set(request);
            responseObserver.onNext(responder.apply(request));
            responseObserver.onCompleted();
        }
    }
}
