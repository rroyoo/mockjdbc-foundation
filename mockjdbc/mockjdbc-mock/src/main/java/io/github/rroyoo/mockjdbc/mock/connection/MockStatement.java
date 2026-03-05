package io.github.rroyoo.mockjdbc.mock.connection;

import io.github.rroyoo.mockjdbc.mock.MockedQuery;
import io.github.rroyoo.mockjdbc.mock.resultset.MockResultSetConverter;

import java.io.InputStream;
import java.io.Reader;
import java.math.BigDecimal;
import java.net.URL;
import java.sql.*;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Mock implementation of JDBC Statement/PreparedStatement/CallableStatement interfaces.
 * Provides stub implementations for all methods, allowing use in tests without a real database.
 *
 * This class is intentionally bloated to satisfy all three statement interfaces.
 * In production code, consider using sealed classes or composition to split responsibilities.
 */
public final class MockStatement implements CallableStatement {

    /**
     * Encapsulates the configuration of a mock statement.
     * Defaults match JDBC standard defaults for forward-only, read-only result sets.
     */
    private record StatementConfig(
        int resultSetType,
        int resultSetConcurrency,
        int resultSetHoldability
    ) {
        static StatementConfig defaults() {
            return new StatementConfig(
                ResultSet.TYPE_FORWARD_ONLY,
                ResultSet.CONCUR_READ_ONLY,
                ResultSet.CLOSE_CURSORS_AT_COMMIT
            );
        }
    }

    private final MockConnection mockConnection;
    private final String sql;
    private final StatementConfig config;
    private final Map<Integer, Object> parameters = new HashMap<>();

    MockStatement(MockConnection mockConnection) throws SQLException {
        this(mockConnection, null, StatementConfig.defaults());
    }

    MockStatement(MockConnection mockConnection, String sql) throws SQLException {
        this(mockConnection, sql, StatementConfig.defaults());
    }

    MockStatement(MockConnection mockConnection, String sql, int resultSetType, int resultSetConcurrency, int resultSetHoldability) throws SQLException {
        this(mockConnection, sql, new StatementConfig(resultSetType, resultSetConcurrency, resultSetHoldability));
    }

    private MockStatement(MockConnection mockConnection, String sql, StatementConfig config) {
        this.mockConnection = mockConnection;
        this.sql = sql;
        this.config = config;
    }

    /**
     * Validates that the parameter index is valid (>= 1).
     *
     * @param parameterIndex the parameter index to validate
     * @throws SQLException if the index is invalid
     */
    private void validateParameterIndex(int parameterIndex) throws SQLException {
        if (parameterIndex < 1) {
            throw new SQLException("Parameter index must be >= 1, got: " + parameterIndex);
        }
    }

    /**
     * Returns a defensive copy of the parameters map.
     *
     * @return map of parameter index to value
     */
    Map<Integer, Object> getParameters() {
        return new HashMap<>(parameters);
    }

    /**
     * Converts stored parameters to a list of ParameterMetadata for gRPC calls.
     *
     * @return list of ParameterMetadata objects
     */
    private List<io.github.rroyoo.mockjdbc.mock.ParameterMetadata> convertParametersToMetadataList() {
        if (parameters.isEmpty()) {
            return Collections.emptyList();
        }

        List<io.github.rroyoo.mockjdbc.mock.ParameterMetadata> result = new ArrayList<>();

        // Get all parameter indices and sort them
        List<Integer> indices = new ArrayList<>(parameters.keySet());
        Collections.sort(indices);

        for (Integer index : indices) {
            Object value = parameters.get(index);
            io.github.rroyoo.mockjdbc.mock.JdbcValue jdbcValue = convertToJdbcValue(value);

            io.github.rroyoo.mockjdbc.mock.ParameterMetadata metadata =
                io.github.rroyoo.mockjdbc.mock.ParameterMetadata.newBuilder()
                    .setIndex(index)
                    .setSqlType(inferSqlType(value))
                    .setTypeName(value != null ? value.getClass().getSimpleName() : "NULL")
                    .setMode(java.sql.ParameterMetaData.parameterModeIn)
                    .setValue(jdbcValue)
                    .build();

            result.add(metadata);
        }

        return result;
    }

    /**
     * Converts a Java object to a JdbcValue protobuf message.
     *
     * @param value the Java object to convert
     * @return the corresponding JdbcValue
     */
    private io.github.rroyoo.mockjdbc.mock.JdbcValue convertToJdbcValue(Object value) {
        io.github.rroyoo.mockjdbc.mock.JdbcValue.Builder builder =
            io.github.rroyoo.mockjdbc.mock.JdbcValue.newBuilder();

        if (value == null) {
            return builder.setIsNull(true).build();
        }

        if (value instanceof String) {
            return builder.setStringVal((String) value).build();
        } else if (value instanceof Integer || value instanceof Long ||
                   value instanceof Short || value instanceof Byte) {
            return builder.setLongVal(((Number) value).longValue()).build();
        } else if (value instanceof Double || value instanceof Float) {
            return builder.setDoubleVal(((Number) value).doubleValue()).build();
        } else if (value instanceof Boolean) {
            return builder.setBoolVal((Boolean) value).build();
        } else if (value instanceof byte[]) {
            return builder.setBytesVal(com.google.protobuf.ByteString.copyFrom((byte[]) value)).build();
        } else if (value instanceof BigDecimal) {
            return builder.setDecimalVal(value.toString()).build();
        } else if (value instanceof Timestamp) {
            Timestamp ts = (Timestamp) value;
            com.google.protobuf.Timestamp pbTimestamp = com.google.protobuf.Timestamp.newBuilder()
                .setSeconds(ts.getTime() / 1000)
                .setNanos(ts.getNanos())
                .build();
            return builder.setTimestampVal(pbTimestamp).build();
        } else if (value instanceof java.sql.Date || value instanceof Time) {
            // Convert SQL Date/Time to timestamp
            long millis = ((java.util.Date) value).getTime();
            com.google.protobuf.Timestamp pbTimestamp = com.google.protobuf.Timestamp.newBuilder()
                .setSeconds(millis / 1000)
                .setNanos((int) ((millis % 1000) * 1_000_000))
                .build();
            return builder.setTimestampVal(pbTimestamp).build();
        } else {
            // For other types, convert to string
            return builder.setStringVal(value.toString()).build();
        }
    }

    /**
     * Infers the SQL type from a Java object.
     *
     * @param value the Java object
     * @return the SQL type code
     */
    private int inferSqlType(Object value) {
        if (value == null) {
            return java.sql.Types.NULL;
        } else if (value instanceof String) {
            return java.sql.Types.VARCHAR;
        } else if (value instanceof Integer) {
            return java.sql.Types.INTEGER;
        } else if (value instanceof Long) {
            return java.sql.Types.BIGINT;
        } else if (value instanceof Short) {
            return java.sql.Types.SMALLINT;
        } else if (value instanceof Byte) {
            return java.sql.Types.TINYINT;
        } else if (value instanceof Double) {
            return java.sql.Types.DOUBLE;
        } else if (value instanceof Float) {
            return java.sql.Types.FLOAT;
        } else if (value instanceof Boolean) {
            return java.sql.Types.BOOLEAN;
        } else if (value instanceof byte[]) {
            return java.sql.Types.VARBINARY;
        } else if (value instanceof BigDecimal) {
            return java.sql.Types.DECIMAL;
        } else if (value instanceof java.sql.Date) {
            return java.sql.Types.DATE;
        } else if (value instanceof Time) {
            return java.sql.Types.TIME;
        } else if (value instanceof Timestamp) {
            return java.sql.Types.TIMESTAMP;
        } else {
            return java.sql.Types.OTHER;
        }
    }

    // Stub helpers to reduce boilerplate
    private static String stubString() { return ""; }
    private static boolean stubBoolean() { return false; }
    private static byte stubByte() { return 0; }
    private static short stubShort() { return 0; }
    private static int stubInt() { return 0; }
    private static long stubLong() { return 0L; }
    private static float stubFloat() { return 0.0f; }
    private static double stubDouble() { return 0.0d; }
    private static <T> T stubNull() { return null; }

    @Override
    public void registerOutParameter(int parameterIndex, int sqlType) throws SQLException {
    }

    @Override
    public void registerOutParameter(int parameterIndex, int sqlType, int scale) throws SQLException {
    }

    @Override
    public boolean wasNull() throws SQLException {
        return stubBoolean();
    }

    @Override
    public String getString(int parameterIndex) throws SQLException {
        return stubString();
    }

    @Override
    public boolean getBoolean(int parameterIndex) throws SQLException {
        return stubBoolean();
    }

    @Override
    public byte getByte(int parameterIndex) throws SQLException {
        return stubByte();
    }

    @Override
    public short getShort(int parameterIndex) throws SQLException {
        return stubShort();
    }

    @Override
    public int getInt(int parameterIndex) throws SQLException {
        return stubInt();
    }

    @Override
    public long getLong(int parameterIndex) throws SQLException {
        return stubLong();
    }

    @Override
    public float getFloat(int parameterIndex) throws SQLException {
        return stubFloat();
    }

    @Override
    public double getDouble(int parameterIndex) throws SQLException {
        return stubDouble();
    }

    @Override
    public BigDecimal getBigDecimal(int parameterIndex, int scale) throws SQLException {
        return stubNull();
    }

    @Override
    public byte[] getBytes(int parameterIndex) throws SQLException {
        return new byte[0];
    }

    @Override
    public Date getDate(int parameterIndex) throws SQLException {
        return null;
    }

    @Override
    public Time getTime(int parameterIndex) throws SQLException {
        return null;
    }

    @Override
    public Timestamp getTimestamp(int parameterIndex) throws SQLException {
        return null;
    }

    @Override
    public Object getObject(int parameterIndex) throws SQLException {
        return null;
    }

    @Override
    public BigDecimal getBigDecimal(int parameterIndex) throws SQLException {
        return null;
    }

    @Override
    public Object getObject(int parameterIndex, Map<String, Class<?>> map) throws SQLException {
        return null;
    }

    @Override
    public Ref getRef(int parameterIndex) throws SQLException {
        return null;
    }

    @Override
    public Blob getBlob(int parameterIndex) throws SQLException {
        return null;
    }

    @Override
    public Clob getClob(int parameterIndex) throws SQLException {
        return null;
    }

    @Override
    public Array getArray(int parameterIndex) throws SQLException {
        return null;
    }

    @Override
    public Date getDate(int parameterIndex, Calendar cal) throws SQLException {
        return null;
    }

    @Override
    public Time getTime(int parameterIndex, Calendar cal) throws SQLException {
        return null;
    }

    @Override
    public Timestamp getTimestamp(int parameterIndex, Calendar cal) throws SQLException {
        return null;
    }

    @Override
    public void registerOutParameter(int parameterIndex, int sqlType, String typeName) throws SQLException {

    }

    @Override
    public void registerOutParameter(String parameterName, int sqlType) throws SQLException {

    }

    @Override
    public void registerOutParameter(String parameterName, int sqlType, int scale) throws SQLException {

    }

    @Override
    public void registerOutParameter(String parameterName, int sqlType, String typeName) throws SQLException {

    }

    @Override
    public URL getURL(int parameterIndex) throws SQLException {
        return null;
    }

    @Override
    public void setURL(String parameterName, URL val) throws SQLException {

    }

    @Override
    public void setNull(String parameterName, int sqlType) throws SQLException {

    }

    @Override
    public void setBoolean(String parameterName, boolean x) throws SQLException {

    }

    @Override
    public void setByte(String parameterName, byte x) throws SQLException {

    }

    @Override
    public void setShort(String parameterName, short x) throws SQLException {

    }

    @Override
    public void setInt(String parameterName, int x) throws SQLException {

    }

    @Override
    public void setLong(String parameterName, long x) throws SQLException {

    }

    @Override
    public void setFloat(String parameterName, float x) throws SQLException {

    }

    @Override
    public void setDouble(String parameterName, double x) throws SQLException {

    }

    @Override
    public void setBigDecimal(String parameterName, BigDecimal x) throws SQLException {

    }

    @Override
    public void setString(String parameterName, String x) throws SQLException {

    }

    @Override
    public void setBytes(String parameterName, byte[] x) throws SQLException {

    }

    @Override
    public void setDate(String parameterName, Date x) throws SQLException {

    }

    @Override
    public void setTime(String parameterName, Time x) throws SQLException {

    }

    @Override
    public void setTimestamp(String parameterName, Timestamp x) throws SQLException {

    }

    @Override
    public void setAsciiStream(String parameterName, InputStream x, int length) throws SQLException {

    }

    @Override
    public void setBinaryStream(String parameterName, InputStream x, int length) throws SQLException {

    }

    @Override
    public void setObject(String parameterName, Object x, int targetSqlType, int scale) throws SQLException {

    }

    @Override
    public void setObject(String parameterName, Object x, int targetSqlType) throws SQLException {

    }

    @Override
    public void setObject(String parameterName, Object x) throws SQLException {

    }

    @Override
    public void setCharacterStream(String parameterName, Reader reader, int length) throws SQLException {

    }

    @Override
    public void setDate(String parameterName, Date x, Calendar cal) throws SQLException {

    }

    @Override
    public void setTime(String parameterName, Time x, Calendar cal) throws SQLException {

    }

    @Override
    public void setTimestamp(String parameterName, Timestamp x, Calendar cal) throws SQLException {

    }

    @Override
    public void setNull(String parameterName, int sqlType, String typeName) throws SQLException {

    }

    @Override
    public String getString(String parameterName) throws SQLException {
        return stubString();
    }

    @Override
    public boolean getBoolean(String parameterName) throws SQLException {
        return stubBoolean();
    }

    @Override
    public byte getByte(String parameterName) throws SQLException {
        return stubByte();
    }

    @Override
    public short getShort(String parameterName) throws SQLException {
        return stubShort();
    }

    @Override
    public int getInt(String parameterName) throws SQLException {
        return stubInt();
    }

    @Override
    public long getLong(String parameterName) throws SQLException {
        return stubLong();
    }

    @Override
    public float getFloat(String parameterName) throws SQLException {
        return stubFloat();
    }

    @Override
    public double getDouble(String parameterName) throws SQLException {
        return stubDouble();
    }

    @Override
    public byte[] getBytes(String parameterName) throws SQLException {
        return new byte[0];
    }

    @Override
    public Date getDate(String parameterName) throws SQLException {
        return stubNull();
    }

    @Override
    public Time getTime(String parameterName) throws SQLException {
        return stubNull();
    }

    @Override
    public Timestamp getTimestamp(String parameterName) throws SQLException {
        return stubNull();
    }

    @Override
    public Object getObject(String parameterName) throws SQLException {
        return stubNull();
    }

    @Override
    public BigDecimal getBigDecimal(String parameterName) throws SQLException {
        return null;
    }

    @Override
    public Object getObject(String parameterName, Map<String, Class<?>> map) throws SQLException {
        return null;
    }

    @Override
    public Ref getRef(String parameterName) throws SQLException {
        return null;
    }

    @Override
    public Blob getBlob(String parameterName) throws SQLException {
        return null;
    }

    @Override
    public Clob getClob(String parameterName) throws SQLException {
        return null;
    }

    @Override
    public Array getArray(String parameterName) throws SQLException {
        return null;
    }

    @Override
    public Date getDate(String parameterName, Calendar cal) throws SQLException {
        return null;
    }

    @Override
    public Time getTime(String parameterName, Calendar cal) throws SQLException {
        return null;
    }

    @Override
    public Timestamp getTimestamp(String parameterName, Calendar cal) throws SQLException {
        return null;
    }

    @Override
    public URL getURL(String parameterName) throws SQLException {
        return null;
    }

    @Override
    public RowId getRowId(int parameterIndex) throws SQLException {
        return null;
    }

    @Override
    public RowId getRowId(String parameterName) throws SQLException {
        return null;
    }

    @Override
    public void setRowId(String parameterName, RowId x) throws SQLException {

    }

    @Override
    public void setNString(String parameterName, String value) throws SQLException {

    }

    @Override
    public void setNCharacterStream(String parameterName, Reader value, long length) throws SQLException {

    }

    @Override
    public void setNClob(String parameterName, NClob value) throws SQLException {

    }

    @Override
    public void setClob(String parameterName, Reader reader, long length) throws SQLException {

    }

    @Override
    public void setBlob(String parameterName, InputStream inputStream, long length) throws SQLException {

    }

    @Override
    public void setNClob(String parameterName, Reader reader, long length) throws SQLException {

    }

    @Override
    public NClob getNClob(int parameterIndex) throws SQLException {
        return null;
    }

    @Override
    public NClob getNClob(String parameterName) throws SQLException {
        return null;
    }

    @Override
    public void setSQLXML(String parameterName, SQLXML xmlObject) throws SQLException {

    }

    @Override
    public SQLXML getSQLXML(int parameterIndex) throws SQLException {
        return null;
    }

    @Override
    public SQLXML getSQLXML(String parameterName) throws SQLException {
        return null;
    }

    @Override
    public String getNString(int parameterIndex) throws SQLException {
        return "";
    }

    @Override
    public String getNString(String parameterName) throws SQLException {
        return "";
    }

    @Override
    public Reader getNCharacterStream(int parameterIndex) throws SQLException {
        return null;
    }

    @Override
    public Reader getNCharacterStream(String parameterName) throws SQLException {
        return null;
    }

    @Override
    public Reader getCharacterStream(int parameterIndex) throws SQLException {
        return null;
    }

    @Override
    public Reader getCharacterStream(String parameterName) throws SQLException {
        return null;
    }

    @Override
    public void setBlob(String parameterName, Blob x) throws SQLException {

    }

    @Override
    public void setClob(String parameterName, Clob x) throws SQLException {

    }

    @Override
    public void setAsciiStream(String parameterName, InputStream x, long length) throws SQLException {

    }

    @Override
    public void setBinaryStream(String parameterName, InputStream x, long length) throws SQLException {

    }

    @Override
    public void setCharacterStream(String parameterName, Reader reader, long length) throws SQLException {

    }

    @Override
    public void setAsciiStream(String parameterName, InputStream x) throws SQLException {

    }

    @Override
    public void setBinaryStream(String parameterName, InputStream x) throws SQLException {

    }

    @Override
    public void setCharacterStream(String parameterName, Reader reader) throws SQLException {

    }

    @Override
    public void setNCharacterStream(String parameterName, Reader value) throws SQLException {

    }

    @Override
    public void setClob(String parameterName, Reader reader) throws SQLException {

    }

    @Override
    public void setBlob(String parameterName, InputStream inputStream) throws SQLException {

    }

    @Override
    public void setNClob(String parameterName, Reader reader) throws SQLException {

    }

    @Override
    public <T> T getObject(int parameterIndex, Class<T> type) throws SQLException {
        return null;
    }

    @Override
    public <T> T getObject(String parameterName, Class<T> type) throws SQLException {
        return null;
    }

    @Override
    public ResultSet executeQuery() throws SQLException {
        if (sql == null) {
            throw new SQLException("No SQL statement provided for PreparedStatement");
        }

        // Convert parameters to metadata list
        List<io.github.rroyoo.mockjdbc.mock.ParameterMetadata> paramList = convertParametersToMetadataList();

        // Query gRPC service for prepared statement
        io.github.rroyoo.mockjdbc.mock.PreparedStatement preparedStatement =
            io.github.rroyoo.mockjdbc.mock.PreparedStatement.newBuilder()
                .setSql(sql)
                .addAllParameters(paramList)
                .build();

        MockedQuery mockedQuery = mockConnection.getQueryServiceAdapter()
            .findMockedQuery(preparedStatement);

        // Convert protobuf SerializedResultSet to JDBC ResultSet
        return MockResultSetConverter.convert(mockedQuery.getResultSet());
    }

    @Override
    public int executeUpdate() throws SQLException {
        return 0;
    }

    @Override
    public void setNull(int parameterIndex, int sqlType) throws SQLException {
        validateParameterIndex(parameterIndex);
        parameters.put(parameterIndex, null);
    }

    @Override
    public void setBoolean(int parameterIndex, boolean x) throws SQLException {
        validateParameterIndex(parameterIndex);
        parameters.put(parameterIndex, x);
    }

    @Override
    public void setByte(int parameterIndex, byte x) throws SQLException {
        validateParameterIndex(parameterIndex);
        parameters.put(parameterIndex, x);
    }

    @Override
    public void setShort(int parameterIndex, short x) throws SQLException {
        validateParameterIndex(parameterIndex);
        parameters.put(parameterIndex, x);
    }

    @Override
    public void setInt(int parameterIndex, int x) throws SQLException {
        validateParameterIndex(parameterIndex);
        parameters.put(parameterIndex, x);
    }

    @Override
    public void setLong(int parameterIndex, long x) throws SQLException {
        validateParameterIndex(parameterIndex);
        parameters.put(parameterIndex, x);
    }

    @Override
    public void setFloat(int parameterIndex, float x) throws SQLException {
        validateParameterIndex(parameterIndex);
        parameters.put(parameterIndex, x);
    }

    @Override
    public void setDouble(int parameterIndex, double x) throws SQLException {
        validateParameterIndex(parameterIndex);
        parameters.put(parameterIndex, x);
    }

    @Override
    public void setBigDecimal(int parameterIndex, BigDecimal x) throws SQLException {
        validateParameterIndex(parameterIndex);
        parameters.put(parameterIndex, x);
    }

    @Override
    public void setString(int parameterIndex, String x) throws SQLException {
        validateParameterIndex(parameterIndex);
        parameters.put(parameterIndex, x);
    }

    @Override
    public void setBytes(int parameterIndex, byte[] x) throws SQLException {
        validateParameterIndex(parameterIndex);
        parameters.put(parameterIndex, x);
    }

    @Override
    public void setDate(int parameterIndex, Date x) throws SQLException {
        validateParameterIndex(parameterIndex);
        parameters.put(parameterIndex, x);
    }

    @Override
    public void setTime(int parameterIndex, Time x) throws SQLException {
        validateParameterIndex(parameterIndex);
        parameters.put(parameterIndex, x);
    }

    @Override
    public void setTimestamp(int parameterIndex, Timestamp x) throws SQLException {
        validateParameterIndex(parameterIndex);
        parameters.put(parameterIndex, x);
    }

    @Override
    public void setAsciiStream(int parameterIndex, InputStream x, int length) throws SQLException {
        validateParameterIndex(parameterIndex);
        parameters.put(parameterIndex, x);
    }

    @Override
    public void setUnicodeStream(int parameterIndex, InputStream x, int length) throws SQLException {
        validateParameterIndex(parameterIndex);
        parameters.put(parameterIndex, x);
    }

    @Override
    public void setBinaryStream(int parameterIndex, InputStream x, int length) throws SQLException {
        validateParameterIndex(parameterIndex);
        parameters.put(parameterIndex, x);
    }

    @Override
    public void clearParameters() throws SQLException {
        parameters.clear();
    }

    @Override
    public void setObject(int parameterIndex, Object x, int targetSqlType) throws SQLException {
        validateParameterIndex(parameterIndex);
        parameters.put(parameterIndex, x);
    }

    @Override
    public void setObject(int parameterIndex, Object x) throws SQLException {
        validateParameterIndex(parameterIndex);
        parameters.put(parameterIndex, x);
    }

    @Override
    public boolean execute() throws SQLException {
        return false;
    }

    @Override
    public void addBatch() throws SQLException {

    }

    @Override
    public void setCharacterStream(int parameterIndex, Reader reader, int length) throws SQLException {
        validateParameterIndex(parameterIndex);
        parameters.put(parameterIndex, reader);
    }

    @Override
    public void setRef(int parameterIndex, Ref x) throws SQLException {
        validateParameterIndex(parameterIndex);
        parameters.put(parameterIndex, x);
    }

    @Override
    public void setBlob(int parameterIndex, Blob x) throws SQLException {
        validateParameterIndex(parameterIndex);
        parameters.put(parameterIndex, x);
    }

    @Override
    public void setClob(int parameterIndex, Clob x) throws SQLException {
        validateParameterIndex(parameterIndex);
        parameters.put(parameterIndex, x);
    }

    @Override
    public void setArray(int parameterIndex, Array x) throws SQLException {
        validateParameterIndex(parameterIndex);
        parameters.put(parameterIndex, x);
    }

    @Override
    public ResultSetMetaData getMetaData() throws SQLException {
        return null;
    }

    @Override
    public void setDate(int parameterIndex, Date x, Calendar cal) throws SQLException {
        validateParameterIndex(parameterIndex);
        parameters.put(parameterIndex, x);
    }

    @Override
    public void setTime(int parameterIndex, Time x, Calendar cal) throws SQLException {
        validateParameterIndex(parameterIndex);
        parameters.put(parameterIndex, x);
    }

    @Override
    public void setTimestamp(int parameterIndex, Timestamp x, Calendar cal) throws SQLException {
        validateParameterIndex(parameterIndex);
        parameters.put(parameterIndex, x);
    }

    @Override
    public void setNull(int parameterIndex, int sqlType, String typeName) throws SQLException {
        validateParameterIndex(parameterIndex);
        parameters.put(parameterIndex, null);
    }

    @Override
    public void setURL(int parameterIndex, URL x) throws SQLException {
        validateParameterIndex(parameterIndex);
        parameters.put(parameterIndex, x);
    }

    @Override
    public ParameterMetaData getParameterMetaData() throws SQLException {
        return null;
    }

    @Override
    public void setRowId(int parameterIndex, RowId x) throws SQLException {
        validateParameterIndex(parameterIndex);
        parameters.put(parameterIndex, x);
    }

    @Override
    public void setNString(int parameterIndex, String value) throws SQLException {
        validateParameterIndex(parameterIndex);
        parameters.put(parameterIndex, value);
    }

    @Override
    public void setNCharacterStream(int parameterIndex, Reader value, long length) throws SQLException {
        validateParameterIndex(parameterIndex);
        parameters.put(parameterIndex, value);
    }

    @Override
    public void setNClob(int parameterIndex, NClob value) throws SQLException {
        validateParameterIndex(parameterIndex);
        parameters.put(parameterIndex, value);
    }

    @Override
    public void setClob(int parameterIndex, Reader reader, long length) throws SQLException {
        validateParameterIndex(parameterIndex);
        parameters.put(parameterIndex, reader);
    }

    @Override
    public void setBlob(int parameterIndex, InputStream inputStream, long length) throws SQLException {
        validateParameterIndex(parameterIndex);
        parameters.put(parameterIndex, inputStream);
    }

    @Override
    public void setNClob(int parameterIndex, Reader reader, long length) throws SQLException {
        validateParameterIndex(parameterIndex);
        parameters.put(parameterIndex, reader);
    }

    @Override
    public void setSQLXML(int parameterIndex, SQLXML xmlObject) throws SQLException {
        validateParameterIndex(parameterIndex);
        parameters.put(parameterIndex, xmlObject);
    }

    @Override
    public void setObject(int parameterIndex, Object x, int targetSqlType, int scaleOrLength) throws SQLException {
        validateParameterIndex(parameterIndex);
        parameters.put(parameterIndex, x);
    }

    @Override
    public void setAsciiStream(int parameterIndex, InputStream x, long length) throws SQLException {
        validateParameterIndex(parameterIndex);
        parameters.put(parameterIndex, x);
    }

    @Override
    public void setBinaryStream(int parameterIndex, InputStream x, long length) throws SQLException {
        validateParameterIndex(parameterIndex);
        parameters.put(parameterIndex, x);
    }

    @Override
    public void setCharacterStream(int parameterIndex, Reader reader, long length) throws SQLException {
        validateParameterIndex(parameterIndex);
        parameters.put(parameterIndex, reader);
    }

    @Override
    public void setAsciiStream(int parameterIndex, InputStream x) throws SQLException {
        validateParameterIndex(parameterIndex);
        parameters.put(parameterIndex, x);
    }

    @Override
    public void setBinaryStream(int parameterIndex, InputStream x) throws SQLException {
        validateParameterIndex(parameterIndex);
        parameters.put(parameterIndex, x);
    }

    @Override
    public void setCharacterStream(int parameterIndex, Reader reader) throws SQLException {
        validateParameterIndex(parameterIndex);
        parameters.put(parameterIndex, reader);
    }

    @Override
    public void setNCharacterStream(int parameterIndex, Reader value) throws SQLException {
        validateParameterIndex(parameterIndex);
        parameters.put(parameterIndex, value);
    }

    @Override
    public void setClob(int parameterIndex, Reader reader) throws SQLException {
        validateParameterIndex(parameterIndex);
        parameters.put(parameterIndex, reader);
    }

    @Override
    public void setBlob(int parameterIndex, InputStream inputStream) throws SQLException {
        validateParameterIndex(parameterIndex);
        parameters.put(parameterIndex, inputStream);
    }

    @Override
    public void setNClob(int parameterIndex, Reader reader) throws SQLException {
        validateParameterIndex(parameterIndex);
        parameters.put(parameterIndex, reader);
    }

    @Override
    public ResultSet executeQuery(String sql) throws SQLException {
        // Query gRPC service for mocked query definition
        io.github.rroyoo.mockjdbc.mock.PlainStatement plainStatement =
            io.github.rroyoo.mockjdbc.mock.PlainStatement.newBuilder()
                .setSql(sql)
                .build();

        MockedQuery mockedQuery = mockConnection.getQueryServiceAdapter()
            .findMockedQuery(plainStatement);

        // Convert protobuf SerializedResultSet to JDBC ResultSet
        return MockResultSetConverter.convert(mockedQuery.getResultSet());
    }

    @Override
    public int executeUpdate(String sql) throws SQLException {
        return 0;
    }

    @Override
    public void close() throws SQLException {

    }

    @Override
    public int getMaxFieldSize() throws SQLException {
        return 0;
    }

    @Override
    public void setMaxFieldSize(int max) throws SQLException {

    }

    @Override
    public int getMaxRows() throws SQLException {
        return 0;
    }

    @Override
    public void setMaxRows(int max) throws SQLException {

    }

    @Override
    public void setEscapeProcessing(boolean enable) throws SQLException {

    }

    @Override
    public int getQueryTimeout() throws SQLException {
        return 0;
    }

    @Override
    public void setQueryTimeout(int seconds) throws SQLException {

    }

    @Override
    public void cancel() throws SQLException {

    }

    @Override
    public SQLWarning getWarnings() throws SQLException {
        return null;
    }

    @Override
    public void clearWarnings() throws SQLException {

    }

    @Override
    public void setCursorName(String name) throws SQLException {

    }

    @Override
    public boolean execute(String sql) throws SQLException {
        return false;
    }

    @Override
    public ResultSet getResultSet() throws SQLException {
        return null;
    }

    @Override
    public int getUpdateCount() throws SQLException {
        return 0;
    }

    @Override
    public boolean getMoreResults() throws SQLException {
        return false;
    }

    @Override
    public void setFetchDirection(int direction) throws SQLException {

    }

    @Override
    public int getFetchDirection() throws SQLException {
        return 0;
    }

    @Override
    public void setFetchSize(int rows) throws SQLException {

    }

    @Override
    public int getFetchSize() throws SQLException {
        return 0;
    }

    @Override
    public int getResultSetConcurrency() throws SQLException {
        return config.resultSetConcurrency();
    }

    @Override
    public int getResultSetType() throws SQLException {
        return config.resultSetType();
    }

    @Override
    public void addBatch(String sql) throws SQLException {

    }

    @Override
    public void clearBatch() throws SQLException {

    }

    @Override
    public int[] executeBatch() throws SQLException {
        return new int[0];
    }

    @Override
    public Connection getConnection() throws SQLException {
        return this.mockConnection;
    }

    @Override
    public boolean getMoreResults(int current) throws SQLException {
        return false;
    }

    @Override
    public ResultSet getGeneratedKeys() throws SQLException {
        return null;
    }

    @Override
    public int executeUpdate(String sql, int autoGeneratedKeys) throws SQLException {
        return 0;
    }

    @Override
    public int executeUpdate(String sql, int[] columnIndexes) throws SQLException {
        return 0;
    }

    @Override
    public int executeUpdate(String sql, String[] columnNames) throws SQLException {
        return 0;
    }

    @Override
    public boolean execute(String sql, int autoGeneratedKeys) throws SQLException {
        return false;
    }

    @Override
    public boolean execute(String sql, int[] columnIndexes) throws SQLException {
        return false;
    }

    @Override
    public boolean execute(String sql, String[] columnNames) throws SQLException {
        return false;
    }

    @Override
    public int getResultSetHoldability() throws SQLException {
        return config.resultSetHoldability();
    }

    @Override
    public boolean isClosed() throws SQLException {
        return false;
    }

    @Override
    public void setPoolable(boolean poolable) throws SQLException {

    }

    @Override
    public boolean isPoolable() throws SQLException {
        return false;
    }

    @Override
    public void closeOnCompletion() throws SQLException {

    }

    @Override
    public boolean isCloseOnCompletion() throws SQLException {
        return false;
    }

    @Override
    public <T> T unwrap(Class<T> iface) throws SQLException {
        return null;
    }

    @Override
    public boolean isWrapperFor(Class<?> iface) throws SQLException {
        return false;
    }
}
