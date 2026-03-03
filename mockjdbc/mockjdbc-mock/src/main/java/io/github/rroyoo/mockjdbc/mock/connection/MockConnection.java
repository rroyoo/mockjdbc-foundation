package io.github.rroyoo.mockjdbc.mock.connection;

import java.sql.*;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.Executor;

/**
 * Mock JDBC Connection implementation.
 * Provides minimal, testable connection support without actual database I/O.
 */
final class MockConnection implements Connection {

    /**
     * Captures the default state of a newly created MockConnection.
     * All fields are immutable; mutable state is managed separately in MockConnection.
     */
    private record DefaultState(
        int holdability,
        int transactionIsolation,
        int networkTimeout,
        String schema,
        String catalog
    ) {
        static DefaultState create() {
            return new DefaultState(
                ResultSet.CLOSE_CURSORS_AT_COMMIT,
                Connection.TRANSACTION_NONE,
                Integer.MAX_VALUE,
                "mockjdbcSchema",
                "mockjdbcCatalog"
            );
        }
    }

    // Immutable state
    private final Properties properties;
    private final Map<String, Class<?>> typeMap;
    private final MockQueryServiceAdapter mockQueryServiceAdapter;
    private final DefaultState defaults;

    // Mutable state
    private boolean closed;
    private boolean autoCommit;
    private boolean readOnly;
    private int holdability;
    private int transactionIsolation;
    private int networkTimeout;
    private String schema;
    private String catalog;
    private SQLWarning sqlWarning;

    MockConnection(Properties properties, MockQueryServiceAdapter mockQueryServiceAdapter) {
        this.properties = properties;
        this.mockQueryServiceAdapter = mockQueryServiceAdapter;
        this.typeMap = new HashMap<>();
        this.defaults = DefaultState.create();

        // Initialize mutable state from defaults
        this.closed = false;
        this.autoCommit = false;
        this.readOnly = false;
        this.holdability = defaults.holdability();
        this.transactionIsolation = defaults.transactionIsolation();
        this.networkTimeout = defaults.networkTimeout();
        this.schema = defaults.schema();
        this.catalog = defaults.catalog();
        this.sqlWarning = new SQLWarning();
    }

    @Override
    public Statement createStatement() throws SQLException {
        return new MockStatement(this);
    }

    @Override
    public PreparedStatement prepareStatement(String sql) throws SQLException {
        return new MockStatement(this, sql);
    }

    @Override
    public CallableStatement prepareCall(String sql) throws SQLException {
        return new MockStatement(this, sql);
    }

    @Override
    public String nativeSQL(String sql) {
        throw new UnsupportedOperationException("Native SQL is not supported by MockConnection");
    }

    @Override
    public void setAutoCommit(boolean autoCommit) {
        this.autoCommit = autoCommit;
    }

    @Override
    public boolean getAutoCommit() {
        return autoCommit;
    }

    @Override
    public void commit() {
    }

    @Override
    public void rollback() {
    }

    @Override
    public void close() {
        this.closed = true;
        try {
            mockQueryServiceAdapter.close();
        } catch (Exception e) {
            throw new RuntimeException("Failed to close MockQueryServiceAdapter", e);
        }
    }

    // ... existing code ...

    private void throwUnsupported(String feature) throws SQLFeatureNotSupportedException {
        throw new SQLFeatureNotSupportedException(feature + " is not supported by MockConnection");
    }

    @Override
    public boolean isClosed() {
        return this.closed;
    }

    @Override
    public DatabaseMetaData getMetaData() {
        return null;
    }

    @Override
    public void setReadOnly(boolean readOnly) {
        this.readOnly = readOnly;
    }

    @Override
    public boolean isReadOnly() {
        return this.readOnly;
    }

    @Override
    public void setCatalog(String catalog) {
        this.catalog = catalog;
    }

    @Override
    public String getCatalog() {
        return this.catalog;
    }

    @Override
    public void setTransactionIsolation(int level) {
        this.transactionIsolation = level;
    }

    @Override
    public int getTransactionIsolation() {
        return this.transactionIsolation;
    }

    @Override
    public SQLWarning getWarnings() {
        return sqlWarning;
    }

    @Override
    public void clearWarnings() {
        sqlWarning = new SQLWarning();
    }

    @Override
    public Statement createStatement(int resultSetType, int resultSetConcurrency) throws SQLException {
        return new MockStatement(this, null, resultSetType, resultSetConcurrency, ResultSet.CLOSE_CURSORS_AT_COMMIT);
    }

    @Override
    public PreparedStatement prepareStatement(String sql, int resultSetType, int resultSetConcurrency) throws SQLException {
        return new MockStatement(this, sql, resultSetType, resultSetConcurrency, ResultSet.CLOSE_CURSORS_AT_COMMIT);
    }

    @Override
    public CallableStatement prepareCall(String sql, int resultSetType, int resultSetConcurrency) throws SQLException {
        return new MockStatement(this, sql, resultSetType, resultSetConcurrency, ResultSet.CLOSE_CURSORS_AT_COMMIT);
    }

    @Override
    public Map<String, Class<?>> getTypeMap() {
        return new HashMap<>(this.typeMap);
    }

    @Override
    public void setTypeMap(Map<String, Class<?>> map) {
        this.typeMap.clear();
        this.typeMap.putAll(map);
    }

    @Override
    public void setHoldability(int holdability) {
        this.holdability = holdability;
    }

    @Override
    public int getHoldability() {
        return this.holdability;
    }

    @Override
    public Savepoint setSavepoint() throws SQLException {
        throwUnsupported("Savepoints");
        return null;
    }

    @Override
    public Savepoint setSavepoint(String name) throws SQLException {
        throwUnsupported("Savepoints");
        return null;
    }

    @Override
    public void rollback(Savepoint savepoint) throws SQLException {
        throwUnsupported("Rollback to savepoint");
    }

    @Override
    public void releaseSavepoint(Savepoint savepoint) throws SQLException {
        throwUnsupported("Release savepoint");
    }

    @Override
    public Statement createStatement(int resultSetType, int resultSetConcurrency, int resultSetHoldability) throws SQLException {
        throwUnsupported("Creating statements with specific result set type, concurrency and holdability");
        return null;
    }

    @Override
    public PreparedStatement prepareStatement(String sql, int resultSetType, int resultSetConcurrency, int resultSetHoldability) throws SQLException {
        throwUnsupported("Preparing sql statements with specific result set type, concurrency and holdability");
        return null;
    }

    @Override
    public CallableStatement prepareCall(String sql, int resultSetType, int resultSetConcurrency, int resultSetHoldability) throws SQLException {
        throwUnsupported("Preparing callable sql statements with specific result set type, concurrency and holdability");
        return null;
    }

    @Override
    public PreparedStatement prepareStatement(String sql, int autoGeneratedKeys) throws SQLException {
        throwUnsupported("Preparing sql statements with auto generated keys");
        return null;
    }

    @Override
    public PreparedStatement prepareStatement(String sql, int[] columnIndexes) throws SQLException {
        throwUnsupported("Preparing sql statements with specific column indexes");
        return null;
    }

    @Override
    public PreparedStatement prepareStatement(String sql, String[] columnNames) throws SQLException {
        throwUnsupported("Preparing sql statements with specific column names");
        return null;
    }

    @Override
    public Clob createClob() throws SQLException {
        throwUnsupported("Creating Clobs");
        return null;
    }

    @Override
    public Blob createBlob() throws SQLException {
        throwUnsupported("Creating Blobs");
        return null;
    }

    @Override
    public NClob createNClob() throws SQLException {
        throwUnsupported("Creating NClobs");
        return null;
    }

    @Override
    public SQLXML createSQLXML() throws SQLException {
        throwUnsupported("Creating SQLXML");
        return null;
    }

    @Override
    public boolean isValid(int timeout) {
        return true;
    }

    @Override
    public void setClientInfo(String name, String value) {
        this.properties.setProperty(name, value);
    }

    @Override
    public void setClientInfo(Properties properties) {
        this.properties.putAll(properties);
    }

    @Override
    public String getClientInfo(String name) {
        return this.properties.getProperty(name);
    }

    @Override
    public Properties getClientInfo() {
        return new Properties(this.properties);
    }

    @Override
    public Array createArrayOf(String typeName, Object[] elements) throws SQLException {
        throwUnsupported("Creating SQL arrays");
        return null;
    }

    @Override
    public Struct createStruct(String typeName, Object[] attributes) throws SQLException {
        throwUnsupported("Creating SQL structs");
        return null;
    }

    @Override
    public void setSchema(String schema) {
        this.schema = schema;
    }

    @Override
    public String getSchema() {
        return this.schema;
    }

    @Override
    public void abort(Executor executor) {
    }

    @Override
    public void setNetworkTimeout(Executor executor, int milliseconds) {
        this.networkTimeout = milliseconds;
    }

    @Override
    public int getNetworkTimeout() {
        return this.networkTimeout;
    }

    @Override
    public <T> T unwrap(Class<T> iface) {
        return iface.cast(this);
    }

    @Override
    public boolean isWrapperFor(Class<?> iface) {
        return iface.isInstance(this);
    }

    public MockQueryServiceAdapter getMockQueryServiceAdapter() {
        return this.mockQueryServiceAdapter;
    }
}
