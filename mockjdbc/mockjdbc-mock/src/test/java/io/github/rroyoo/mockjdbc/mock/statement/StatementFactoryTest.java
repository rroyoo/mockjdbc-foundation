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
import java.util.Calendar;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
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

    @Test
    @DisplayName("Given a prepared statement, when setNull(int,int,String) is called, then a null parameter is sent with the given type name")
    void shouldSendNullParameterWithTypeName() throws Exception {
        service.responder = request -> mockedQuery(SerializedResultSet.getDefaultInstance());

        try (var statement = statementFactory.prepareStatement("SELECT ?")) {
            statement.setNull(1, Types.VARCHAR, "VARCHAR");
            statement.executeUpdate();
        }

        var req = service.lastRequest.get();
        assertNotNull(req);
        assertEquals(1, req.getParametersCount());
        assertTrue(req.getParameters(0).getValue().getIsNull());
        assertEquals("VARCHAR", req.getParameters(0).getTypeName());
    }

    @Test
    @DisplayName("Given a prepared statement, when setObject(int,Object,int) is called, then the value is sent")
    void shouldSendObjectParameterWithTargetType() throws Exception {
        service.responder = request -> mockedQuery(SerializedResultSet.getDefaultInstance());

        try (var statement = statementFactory.prepareStatement("SELECT ?")) {
            statement.setObject(1, "hello", Types.VARCHAR);
            statement.executeUpdate();
        }

        var req = service.lastRequest.get();
        assertNotNull(req);
        assertEquals(1, req.getParametersCount());
        assertEquals("hello", req.getParameters(0).getValue().getStringVal());
    }

    @Test
    @DisplayName("Given a prepared statement, when temporal Calendar overloads are called, then they do not throw")
    void shouldAcceptCalendarOverloadsWithoutThrowing() throws Exception {
        service.responder = request -> mockedQuery(SerializedResultSet.getDefaultInstance());

        var cal = Calendar.getInstance();
        var date = Date.valueOf("2026-03-13");
        var time = Time.valueOf("10:00:00");
        var timestamp = Timestamp.valueOf("2026-03-13 10:00:00");

        try (var statement = statementFactory.prepareStatement("SELECT ?,?,?")) {
            assertDoesNotThrow(() -> statement.setDate(1, date, cal));
            assertDoesNotThrow(() -> statement.setTime(2, time, cal));
            assertDoesNotThrow(() -> statement.setTimestamp(3, timestamp, cal));
            statement.executeUpdate();
        }

        assertEquals(3, service.lastRequest.get().getParametersCount());
    }

    @Test
    @DisplayName("Given a prepared statement with bound parameters, when getParameterMetaData is called, then it returns count and types")
    void shouldReturnParameterMetaDataWithCorrectCountAndTypes() throws Exception {
        service.responder = request -> mockedQuery(SerializedResultSet.getDefaultInstance());

        try (var statement = statementFactory.prepareStatement("SELECT ?,?")) {
            statement.setString(1, "hello");
            statement.setInt(2, 42);

            var meta = statement.getParameterMetaData();
            assertNotNull(meta);
            assertEquals(2, meta.getParameterCount());
            assertEquals(Types.VARCHAR, meta.getParameterType(1));
            assertEquals(Types.INTEGER, meta.getParameterType(2));
        }
    }

    @Test
    @DisplayName("Given a prepared statement, when getMetaData is called before execution, then it returns null")
    void shouldReturnNullForGetMetaDataBeforeExecution() throws Exception {
        try (var statement = statementFactory.prepareStatement("SELECT ?")) {
            assertNull(statement.getMetaData());
        }
    }

    @Test
    @DisplayName("Given a prepared statement, when setURL is called, then the URL string is sent in the lookup request")
    void shouldSendUrlParameterToGrpc() throws Exception {
        service.responder = request -> mockedQuery(SerializedResultSet.getDefaultInstance());

        var url = new java.net.URL("https://example.com");

        try (var statement = statementFactory.prepareStatement("SELECT ?")) {
            statement.setURL(1, url);
            statement.executeUpdate();
        }

        var req = service.lastRequest.get();
        assertNotNull(req);
        assertEquals(1, req.getParametersCount());
        assertEquals("https://example.com", req.getParameters(0).getValue().getStringVal());
    }

    @Test
    @DisplayName("Given a prepared statement, when setNString is called, then the value is sent as NVARCHAR")
    void shouldSendNStringParameterToGrpc() throws Exception {
        service.responder = request -> mockedQuery(SerializedResultSet.getDefaultInstance());

        try (var statement = statementFactory.prepareStatement("SELECT ?")) {
            statement.setNString(1, "hélo");
            statement.executeUpdate();
        }

        var req = service.lastRequest.get();
        assertNotNull(req);
        assertEquals(1, req.getParametersCount());
        assertEquals("hélo", req.getParameters(0).getValue().getStringVal());
        assertEquals(java.sql.Types.NVARCHAR, req.getParameters(0).getSqlType());
    }

    @Test
    @DisplayName("Given a prepared statement, when setBinaryStream is called, then the bytes are sent in the lookup request")
    void shouldSendBinaryStreamParameterToGrpc() throws Exception {
        service.responder = request -> mockedQuery(SerializedResultSet.getDefaultInstance());

        var data = new byte[]{10, 20, 30};

        try (var statement = statementFactory.prepareStatement("SELECT ?")) {
            statement.setBinaryStream(1, new java.io.ByteArrayInputStream(data));
            statement.executeUpdate();
        }

        var req = service.lastRequest.get();
        assertNotNull(req);
        assertEquals(com.google.protobuf.ByteString.copyFrom(data), req.getParameters(0).getValue().getBytesVal());
    }

    @Test
    @DisplayName("Given a prepared statement, when setCharacterStream is called, then the string content is sent as CLOB")
    void shouldSendCharacterStreamParameterToGrpc() throws Exception {
        service.responder = request -> mockedQuery(SerializedResultSet.getDefaultInstance());

        try (var statement = statementFactory.prepareStatement("SELECT ?")) {
            statement.setCharacterStream(1, new java.io.StringReader("stream content"));
            statement.executeUpdate();
        }

        var req = service.lastRequest.get();
        assertNotNull(req);
        assertEquals("stream content", req.getParameters(0).getValue().getStringVal());
        assertEquals(java.sql.Types.CLOB, req.getParameters(0).getSqlType());
    }

    @Test
    @DisplayName("Given a prepared statement, when setAsciiStream is called, then the ASCII string is sent in the lookup request")
    void shouldSendAsciiStreamParameterToGrpc() throws Exception {
        service.responder = request -> mockedQuery(SerializedResultSet.getDefaultInstance());

        var data = "ascii text".getBytes(java.nio.charset.StandardCharsets.US_ASCII);

        try (var statement = statementFactory.prepareStatement("SELECT ?")) {
            statement.setAsciiStream(1, new java.io.ByteArrayInputStream(data));
            statement.executeUpdate();
        }

        var req = service.lastRequest.get();
        assertNotNull(req);
        assertEquals("ascii text", req.getParameters(0).getValue().getStringVal());
    }

    @Test
    @DisplayName("Given a prepared statement, when setBlob with InputStream is called, then the bytes are sent in the lookup request")
    void shouldSendBlobInputStreamParameterToGrpc() throws Exception {
        service.responder = request -> mockedQuery(SerializedResultSet.getDefaultInstance());

        var data = new byte[]{1, 2, 3};

        try (var statement = statementFactory.prepareStatement("SELECT ?")) {
            statement.setBlob(1, new java.io.ByteArrayInputStream(data));
            statement.executeUpdate();
        }

        var req = service.lastRequest.get();
        assertNotNull(req);
        assertEquals(com.google.protobuf.ByteString.copyFrom(data), req.getParameters(0).getValue().getBytesVal());
        assertEquals(java.sql.Types.BLOB, req.getParameters(0).getSqlType());
    }

    @Test
    @DisplayName("Given a prepared statement, when setClob with Reader is called, then the text is sent in the lookup request")
    void shouldSendClobReaderParameterToGrpc() throws Exception {
        service.responder = request -> mockedQuery(SerializedResultSet.getDefaultInstance());

        try (var statement = statementFactory.prepareStatement("SELECT ?")) {
            statement.setClob(1, new java.io.StringReader("clob text"));
            statement.executeUpdate();
        }

        var req = service.lastRequest.get();
        assertNotNull(req);
        assertEquals("clob text", req.getParameters(0).getValue().getStringVal());
        assertEquals(java.sql.Types.CLOB, req.getParameters(0).getSqlType());
    }

    @Test
    @DisplayName("Given a callable statement, when OUT parameter is registered by index, then typed getters return default values")
    void shouldReturnDefaultOutValuesForRegisteredIndexParameter() throws Exception {
        try (var callable = statementFactory.prepareCall("{ call demo(?) }")) {
            callable.registerOutParameter(1, Types.INTEGER);

            assertEquals(0, callable.getInt(1));
            assertNull(callable.getObject(1));
            assertTrue(callable.wasNull());
        }
    }

    @Test
    @DisplayName("Given a callable statement, when OUT parameter is registered by name, then typed getters return default values")
    void shouldReturnDefaultOutValuesForRegisteredNamedParameter() throws Exception {
        try (var callable = statementFactory.prepareCall("{ call demo(?) }")) {
            callable.registerOutParameter("out_status", Types.VARCHAR);

            assertNull(callable.getString("out_status"));
            assertEquals(0, callable.getInt("out_status"));
            assertTrue(callable.wasNull());
        }
    }

    @Test
    @DisplayName("Given a callable statement, when OUT parameter is not registered, then getter throws SQLException")
    void shouldFailWhenReadingUnregisteredOutParameter() throws Exception {
        try (var callable = statementFactory.prepareCall("{ call demo(?) }")) {
            assertThrows(java.sql.SQLException.class, () -> callable.getInt(1));
            assertThrows(java.sql.SQLException.class, () -> callable.getString("missing_out"));
        }
    }

    @Test
    @DisplayName("Given a callable statement with registered OUT index, when Calendar and typed getObject overloads are used, then they return default values")
    void shouldSupportCalendarAndTypedObjectOutGettersByIndex() throws Exception {
        try (var callable = statementFactory.prepareCall("{ call demo(?) }")) {
            callable.registerOutParameter(1, Types.TIMESTAMP);

            assertNull(callable.getTimestamp(1, Calendar.getInstance()));
            assertNull(callable.getObject(1, String.class));
            assertTrue(callable.wasNull());
        }
    }

    @Test
    @DisplayName("Given a callable statement with registered OUT name, when Calendar and typed getObject overloads are used, then they return default values")
    void shouldSupportCalendarAndTypedObjectOutGettersByName() throws Exception {
        try (var callable = statementFactory.prepareCall("{ call demo(?) }")) {
            callable.registerOutParameter("out_created", Types.DATE);

            assertNull(callable.getDate("out_created", Calendar.getInstance()));
            assertNull(callable.getObject("out_created", String.class));
            assertTrue(callable.wasNull());
        }
    }

    @Test
    @DisplayName("Given a prepared statement, when setObject receives Float/Date/Timestamp, then values are encoded with expected JDBC types")
    void shouldEncodeAdditionalSetObjectJavaTypes() throws Exception {
        service.responder = request -> mockedQuery(SerializedResultSet.getDefaultInstance());

        var date = Date.valueOf("2026-03-13");
        var ts = Timestamp.valueOf("2026-03-13 11:22:33");

        try (var statement = statementFactory.prepareStatement("SELECT ?,?,?")) {
            statement.setObject(1, 1.5f);
            statement.setObject(2, date);
            statement.setObject(3, ts);
            statement.executeUpdate();
        }

        var req = service.lastRequest.get();
        assertNotNull(req);
        assertEquals(3, req.getParametersCount());
        assertEquals(Types.FLOAT, req.getParameters(0).getSqlType());
        assertEquals(1.5d, req.getParameters(0).getValue().getDoubleVal(), 0.0001d);
        assertEquals(Types.DATE, req.getParameters(1).getSqlType());
        assertEquals(date.toString(), req.getParameters(1).getValue().getStringVal());
        assertEquals(Types.TIMESTAMP, req.getParameters(2).getSqlType());
        assertEquals(ts.toString(), req.getParameters(2).getValue().getStringVal());
    }

    @Test
    @DisplayName("Given a prepared statement, when setObject uses targetSqlType, then parameter type follows targetSqlType")
    void shouldHonorTargetSqlTypeInSetObject() throws Exception {
        service.responder = request -> mockedQuery(SerializedResultSet.getDefaultInstance());

        try (var statement = statementFactory.prepareStatement("SELECT ?,?")) {
            statement.setObject(1, 123, Types.BIGINT);
            statement.setObject(2, "42", Types.VARCHAR);
            statement.executeUpdate();
        }

        var req = service.lastRequest.get();
        assertNotNull(req);
        assertEquals(2, req.getParametersCount());
        assertEquals(Types.BIGINT, req.getParameters(0).getSqlType());
        assertEquals(123L, req.getParameters(0).getValue().getLongVal());
        assertEquals(Types.VARCHAR, req.getParameters(1).getSqlType());
        assertEquals("42", req.getParameters(1).getValue().getStringVal());
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
