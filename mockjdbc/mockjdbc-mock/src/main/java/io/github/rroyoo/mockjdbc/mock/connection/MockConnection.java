package io.github.rroyoo.mockjdbc.mock.connection;

import java.sql.*;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

final class MockConnection implements Connection {

    private final Properties properties;

    private boolean closed;
    private boolean autoCommit;
    private boolean readOnly;

    private int holdability;
    private int transactionIsolation;
    private int networkTimeout;

    private String schema;
    private String catalog;

    private final Map<String, Class<?>> typeMap;

    private SQLWarning sqlWarning;

    private final MockQueryServiceAdapter mockQueryServiceAdapter;

    MockConnection(Properties properties, MockQueryServiceAdapter mockQueryServiceAdapter) {
        this.properties = properties;
        this.closed = false;
        this.autoCommit = false;
        this.readOnly = false;

        this.holdability = ResultSet.CLOSE_CURSORS_AT_COMMIT;
        this.transactionIsolation = Connection.TRANSACTION_NONE;
        this.networkTimeout = Integer.MAX_VALUE;

        this.schema = "mockjdbcSchema";
        this.catalog = "mockjdbcCatalog";

        this.typeMap = new ConcurrentHashMap<>();

        this.sqlWarning = new SQLWarning();

        this.mockQueryServiceAdapter = mockQueryServiceAdapter;
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
            this.mockQueryServiceAdapter.close();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
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
        throw new SQLFeatureNotSupportedException("Savepoints are not supported by MockConnection");
    }

    @Override
    public Savepoint setSavepoint(String name) throws SQLException {
        throw new SQLFeatureNotSupportedException("Savepoints are not supported by MockConnection");
    }

    @Override
    public void rollback(Savepoint savepoint) throws SQLException {
        throw new SQLFeatureNotSupportedException("Rollback to savepoint is not supported by MockConnection");
    }

    @Override
    public void releaseSavepoint(Savepoint savepoint) throws SQLException {
        throw new SQLFeatureNotSupportedException("Release savepoint is not supported by MockConnection");
    }

    @Override
    public Statement createStatement(int resultSetType, int resultSetConcurrency, int resultSetHoldability) throws SQLException {
        throw new SQLFeatureNotSupportedException("Creating statements with specific result set type, concurrency and holdability is not supported by MockConnection");
    }

    @Override
    public PreparedStatement prepareStatement(String sql, int resultSetType, int resultSetConcurrency, int resultSetHoldability) throws SQLException {
        throw new SQLFeatureNotSupportedException("Preparing sql statements with specific result set type, concurrency and holdability is not supported by MockConnection");
    }

    @Override
    public CallableStatement prepareCall(String sql, int resultSetType, int resultSetConcurrency, int resultSetHoldability) throws SQLException {
        throw new SQLFeatureNotSupportedException("Preparing callable sql statements with specific result set type, concurrency and holdability is not supported by MockConnection");
    }

    @Override
    public PreparedStatement prepareStatement(String sql, int autoGeneratedKeys) throws SQLException {
        throw new SQLFeatureNotSupportedException("Preparing sql statements with auto generated keys is not supported by MockConnection");
    }

    @Override
    public PreparedStatement prepareStatement(String sql, int[] columnIndexes) throws SQLException {
        throw new SQLFeatureNotSupportedException("Preparing sql statements with specific column indexes is not supported by MockConnection");
    }

    @Override
    public PreparedStatement prepareStatement(String sql, String[] columnNames) throws SQLException {
        throw new SQLFeatureNotSupportedException("Preparing sql statements with specific column names is not supported by MockConnection");
    }

    @Override
    public Clob createClob() throws SQLException {
        throw new SQLFeatureNotSupportedException("Creating Clobs is not supported by MockConnection");
    }

    @Override
    public Blob createBlob() throws SQLException {
        throw new SQLFeatureNotSupportedException("Creating Blobs is not supported by MockConnection");
    }

    @Override
    public NClob createNClob() throws SQLException {
        throw new SQLFeatureNotSupportedException("Creating NClobs is not supported by MockConnection");
    }

    @Override
    public SQLXML createSQLXML() throws SQLException {
        throw new SQLFeatureNotSupportedException("Creating SQLXML is not supported by MockConnection");
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
        throw new SQLFeatureNotSupportedException("Creating SQL arrays is not supported by MockConnection");
    }

    @Override
    public Struct createStruct(String typeName, Object[] attributes) throws SQLException {
        throw new SQLFeatureNotSupportedException("Creating SQL structs is not supported by MockConnection");
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
