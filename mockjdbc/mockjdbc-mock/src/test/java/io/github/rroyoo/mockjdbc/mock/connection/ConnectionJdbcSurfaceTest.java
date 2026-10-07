package io.github.rroyoo.mockjdbc.mock.connection;

import io.github.rroyoo.mockjdbc.mock.driver.MockConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.Types;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConnectionJdbcSurfaceTest {

    @Test
    @DisplayName("Given an open connection, when isValid is called, then it returns true")
    void shouldReportOpenConnectionAsValid() throws Exception {
        var connection = ConnectionFactory.create(mockConfig());

        assertTrue(connection.isValid(0));
        assertTrue(connection.isValid(5));
    }

    @Test
    @DisplayName("Given a closed connection, when isValid is called, then it returns false")
    void shouldReportClosedConnectionAsInvalid() throws Exception {
        var connection = ConnectionFactory.create(mockConfig());
        connection.close();

        assertFalse(connection.isValid(1));
    }

    @Test
    @DisplayName("Given a negative timeout, when isValid is called, then it throws SQLException")
    void shouldRejectNegativeValidationTimeout() {
        var connection = ConnectionFactory.create(mockConfig());

        assertThrows(SQLException.class, () -> connection.isValid(-1));
    }

    @Test
    @DisplayName("Given an open connection, when abort is called, then it is closed and the executor runs the release")
    void shouldCloseConnectionOnAbortUsingExecutor() throws Exception {
        var connection = ConnectionFactory.create(mockConfig());
        var executed = new AtomicBoolean(false);

        connection.abort(command -> { executed.set(true); command.run(); });

        assertTrue(connection.isClosed());
        assertTrue(executed.get());
    }

    @Test
    @DisplayName("Given an aborted connection, when state-dependent methods are called, then SQLException is thrown")
    void shouldRejectStateDependentMethodsAfterAbort() throws Exception {
        var connection = ConnectionFactory.create(mockConfig());
        connection.abort(Runnable::run);

        assertThrows(SQLException.class, connection::createStatement);
        assertThrows(SQLException.class, () -> connection.prepareStatement("select 1"));
        assertThrows(SQLException.class, connection::getAutoCommit);
        assertThrows(SQLException.class, connection::commit);
    }

    @Test
    @DisplayName("Given a null executor, when abort is called, then it throws SQLException and stays open")
    void shouldRejectAbortWithoutExecutor() throws Exception {
        var connection = ConnectionFactory.create(mockConfig());

        assertThrows(SQLException.class, () -> connection.abort(null));
        assertFalse(connection.isClosed());
    }

    @Test
    @DisplayName("Given a closed connection, when abort is called, then it is a no-op")
    void shouldIgnoreAbortOnClosedConnection() throws Exception {
        var connection = ConnectionFactory.create(mockConfig());
        connection.close();
        var executed = new AtomicBoolean(false);

        connection.abort(command -> executed.set(true));

        assertFalse(executed.get());
    }

    @Test
    @DisplayName("Given a connection, when unwrap is called with a supported interface, then it returns the connection itself")
    void shouldUnwrapToSupportedInterface() throws Exception {
        var connection = ConnectionFactory.create(mockConfig());

        assertTrue(connection.isWrapperFor(Connection.class));
        assertTrue(connection.isWrapperFor(java.sql.Wrapper.class));
        assertSame(connection, connection.unwrap(Connection.class));
    }

    @Test
    @DisplayName("Given a connection, when unwrap is called with an unrelated interface, then it throws SQLException")
    void shouldRejectUnwrapOfUnrelatedInterface() throws Exception {
        var connection = ConnectionFactory.create(mockConfig());

        assertFalse(connection.isWrapperFor(java.sql.Statement.class));
        assertThrows(SQLException.class, () -> connection.unwrap(java.sql.Statement.class));
        assertThrows(SQLException.class, () -> connection.unwrap(null));
        assertThrows(SQLException.class, () -> connection.isWrapperFor(null));
    }

    @Test
    @DisplayName("Given a connection, when getMetaData is called, then it describes the mock product, driver and URL")
    void shouldExposeProductDriverAndUrlMetaData() throws Exception {
        var connection = ConnectionFactory.create(mockConfig());

        var metaData = connection.getMetaData();

        assertNotNull(metaData);
        assertEquals("MockJDBC", metaData.getDatabaseProductName());
        assertEquals("MockJDBC Driver", metaData.getDriverName());
        assertEquals("jdbc:mock://localhost:50051", metaData.getURL());
        assertEquals(4, metaData.getJDBCMajorVersion());
        assertSame(connection, metaData.getConnection());
        assertSame(metaData, connection.getMetaData());
    }

    @Test
    @DisplayName("Given connection metadata, when an unsupported catalog method is called, then it throws SQLFeatureNotSupportedException")
    void shouldRejectCatalogQueriesInMetaData() throws Exception {
        var metaData = ConnectionFactory.create(mockConfig()).getMetaData();

        assertThrows(SQLFeatureNotSupportedException.class, () -> metaData.getTables(null, null, "%", null));
        assertThrows(SQLFeatureNotSupportedException.class, () -> metaData.getTypeInfo());
    }

    @Test
    @DisplayName("Given connection metadata, when unwrap is used, then it follows the Wrapper contract")
    void shouldUnwrapMetaData() throws Exception {
        var metaData = ConnectionFactory.create(mockConfig()).getMetaData();

        assertSame(metaData, metaData.unwrap(DatabaseMetaData.class));
        assertTrue(metaData.isWrapperFor(DatabaseMetaData.class));
        assertFalse(metaData.isWrapperFor(Connection.class));
        assertThrows(SQLException.class, () -> metaData.unwrap(Connection.class));
    }

    @Test
    @DisplayName("Given a new connection, when the type map is read, then it is empty and independent of later changes")
    void shouldReturnEmptyTypeMapByDefault() throws Exception {
        var connection = ConnectionFactory.create(mockConfig());

        var typeMap = connection.getTypeMap();
        typeMap.put("ignored", String.class);

        assertTrue(connection.getTypeMap().isEmpty());
    }

    @Test
    @DisplayName("Given a type map, when setTypeMap is called, then getTypeMap returns a copy of it")
    void shouldStoreTypeMapPerConnection() throws Exception {
        var first = ConnectionFactory.create(mockConfig());
        var second = ConnectionFactory.create(mockConfig());
        var typeMap = new HashMap<String, Class<?>>();
        typeMap.put("ADDRESS", String.class);

        first.setTypeMap(typeMap);
        typeMap.clear();

        assertEquals(Map.of("ADDRESS", String.class), first.getTypeMap());
        assertTrue(second.getTypeMap().isEmpty());
    }

    @Test
    @DisplayName("Given a null type map, when setTypeMap is called, then it throws SQLException")
    void shouldRejectNullTypeMap() {
        var connection = ConnectionFactory.create(mockConfig());

        assertThrows(SQLException.class, () -> connection.setTypeMap(null));
    }

    @Test
    @DisplayName("Given a connection, when LOB factories are called, then writable empty LOBs are returned")
    void shouldCreateInMemoryLobs() throws Exception {
        var connection = ConnectionFactory.create(mockConfig());

        var blob = connection.createBlob();
        blob.setBytes(1, new byte[]{1, 2, 3});
        var clob = connection.createClob();
        clob.setString(1, "hello");
        var nclob = connection.createNClob();
        nclob.setString(1, "hola");

        assertEquals(3, blob.length());
        assertEquals("hello", clob.getSubString(1, 5));
        assertEquals("hola", nclob.getSubString(1, 4));
        assertNotNull(nclob.toString());
    }

    @Test
    @DisplayName("Given a connection, when createSQLXML is called, then it throws SQLFeatureNotSupportedException")
    void shouldRejectSqlXml() {
        var connection = ConnectionFactory.create(mockConfig());

        assertThrows(SQLFeatureNotSupportedException.class, connection::createSQLXML);
    }

    @Test
    @DisplayName("Given a type name and elements, when createArrayOf is called, then the array exposes them")
    void shouldCreateArray() throws Exception {
        var connection = ConnectionFactory.create(mockConfig());

        var array = connection.createArrayOf("INTEGER", new Object[]{1, 2, 3});

        assertEquals("INTEGER", array.getBaseTypeName());
        assertEquals(Types.INTEGER, array.getBaseType());
        assertArrayEquals(new Object[]{1, 2, 3}, (Object[]) array.getArray());
        assertArrayEquals(new Object[]{2, 3}, (Object[]) array.getArray(2, 2));
        assertThrows(SQLException.class, () -> array.getArray(3, 5));
        assertThrows(SQLException.class, () -> array.getArray(Long.MAX_VALUE, 2));
        assertThrows(SQLFeatureNotSupportedException.class, array::getResultSet);
        array.free();
        assertThrows(SQLException.class, array::getArray);
        assertThrows(SQLException.class, array::getResultSet);
    }

    @Test
    @DisplayName("Given a type name and attributes, when createStruct is called, then the struct exposes them")
    void shouldCreateStruct() throws Exception {
        var connection = ConnectionFactory.create(mockConfig());

        var struct = connection.createStruct("ADDRESS", new Object[]{"Main St", 42});

        assertEquals("ADDRESS", struct.getSQLTypeName());
        assertArrayEquals(new Object[]{"Main St", 42}, struct.getAttributes());
    }

    @Test
    @DisplayName("Given null arguments, when createArrayOf or createStruct is called, then SQLException is thrown")
    void shouldRejectNullStructuredTypeArguments() {
        var connection = ConnectionFactory.create(mockConfig());

        assertThrows(SQLException.class, () -> connection.createArrayOf(null, new Object[0]));
        assertThrows(SQLException.class, () -> connection.createArrayOf("INTEGER", null));
        assertThrows(SQLException.class, () -> connection.createStruct(null, new Object[0]));
        assertThrows(SQLException.class, () -> connection.createStruct("ADDRESS", null));
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("operationsRequiringOpenConnection")
    @DisplayName("Given a closed connection, when a state-dependent operation is called, then it throws SQLException")
    void shouldRejectOperationsOnClosedConnection(String name, ConnectionOperation operation) throws Exception {
        var connection = ConnectionFactory.create(mockConfig());
        connection.close();

        assertThrows(SQLException.class, () -> operation.run(connection));
    }

    static Stream<Object[]> operationsRequiringOpenConnection() {
        return Stream.of(
                op("getMetaData", Connection::getMetaData),
                op("getTypeMap", Connection::getTypeMap),
                op("setTypeMap", c -> c.setTypeMap(Map.of())),
                op("nativeSQL", c -> c.nativeSQL("SELECT 1")),
                op("createBlob", Connection::createBlob),
                op("createClob", Connection::createClob),
                op("createNClob", Connection::createNClob),
                op("createArrayOf", c -> c.createArrayOf("INTEGER", new Object[0])),
                op("createStruct", c -> c.createStruct("ADDRESS", new Object[0]))
        );
    }

    private static Object[] op(String name, ConnectionOperation operation) {
        return new Object[]{name, operation};
    }

    @FunctionalInterface
    interface ConnectionOperation {
        void run(Connection connection) throws Exception;
    }

    private static MockConfig mockConfig() {
        return new MockConfig(new MockConfig.MockServer("localhost", 50051), new Properties());
    }
}
