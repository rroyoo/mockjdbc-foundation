package io.github.rroyoo.mockjdbc.mock.statement;

import io.github.rroyoo.mockjdbc.mock.JdbcValue;
import io.github.rroyoo.mockjdbc.mock.ParameterMetadata;
import io.github.rroyoo.mockjdbc.mock.driver.MockConfig;

import java.math.BigDecimal;
import java.io.InputStream;
import java.io.Reader;
import java.net.URL;
import java.sql.Array;
import java.sql.Blob;
import java.sql.Clob;
import java.sql.Date;
import java.sql.NClob;
import java.sql.ParameterMetaData;
import java.sql.Ref;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.RowId;
import java.sql.SQLException;
import java.sql.SQLXML;
import java.sql.Time;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Comparator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

final class PreparedStatementQueryHandler {

    private final StatementLifecycleHandler lifecycle;
    private final StatementExecutionStateHandler executionState;
    private final GrpcMockQueryClient client;
    private final String sql;
    private final GeneratedKeysHandler generatedKeys;
    private final boolean returnGeneratedKeys;
    private final ConcurrentHashMap<Integer, ParameterMetadata> parameters = new ConcurrentHashMap<>();
    private final java.util.List<java.util.List<ParameterMetadata>> parameterBatches = new ArrayList<>();

    PreparedStatementQueryHandler(MockConfig mockConfig,
                                  StatementLifecycleHandler lifecycle,
                                  StatementExecutionStateHandler executionState,
                                  GeneratedKeysHandler generatedKeys,
                                  boolean returnGeneratedKeys,
                                  String sql) {
        this.lifecycle = lifecycle;
        this.executionState = executionState;
        this.generatedKeys = generatedKeys;
        this.returnGeneratedKeys = returnGeneratedKeys;
        this.client = new GrpcMockQueryClient(mockConfig);
        this.sql = sql;
    }

    public void setString(int index, String value) throws SQLException {
        parameters.put(index, parameter(index, Types.VARCHAR, "VARCHAR", jdbcValue(value)));
    }

    public void setInt(int index, int value) throws SQLException {
        parameters.put(index, parameter(index, Types.INTEGER, "INTEGER", JdbcValue.newBuilder().setLongVal(value).build()));
    }

    public void setLong(int index, long value) throws SQLException {
        parameters.put(index, parameter(index, Types.BIGINT, "BIGINT", JdbcValue.newBuilder().setLongVal(value).build()));
    }

    public void setBoolean(int index, boolean value) throws SQLException {
        parameters.put(index, parameter(index, Types.BOOLEAN, "BOOLEAN", JdbcValue.newBuilder().setBoolVal(value).build()));
    }

    public void setDouble(int index, double value) throws SQLException {
        parameters.put(index, parameter(index, Types.DOUBLE, "DOUBLE", JdbcValue.newBuilder().setDoubleVal(value).build()));
    }

    public void setFloat(int index, float value) throws SQLException {
        parameters.put(index, parameter(index, Types.FLOAT, "FLOAT", JdbcValue.newBuilder().setDoubleVal(value).build()));
    }

    public void setShort(int index, short value) throws SQLException {
        parameters.put(index, parameter(index, Types.SMALLINT, "SMALLINT", JdbcValue.newBuilder().setLongVal(value).build()));
    }

    public void setByte(int index, byte value) throws SQLException {
        parameters.put(index, parameter(index, Types.TINYINT, "TINYINT", JdbcValue.newBuilder().setLongVal(value).build()));
    }

    public void setBytes(int index, byte[] value) throws SQLException {
        if (value == null) {
            setNull(index, Types.BINARY);
        } else {
            parameters.put(index, parameter(index, Types.BINARY, "BINARY",
                    JdbcValue.newBuilder().setBytesVal(com.google.protobuf.ByteString.copyFrom(value)).build()));
        }
    }

    public void setBigDecimal(int index, BigDecimal value) throws SQLException {
        if (value == null) {
            setNull(index, Types.DECIMAL);
        } else {
            parameters.put(index, parameter(index, Types.DECIMAL, "DECIMAL",
                    JdbcValue.newBuilder().setDecimalVal(value.toPlainString()).build()));
        }
    }

    public void setDate(int index, Date value) throws SQLException {
        if (value == null) {
            setNull(index, Types.DATE);
        } else {
            parameters.put(index, parameter(index, Types.DATE, "DATE",
                    JdbcValue.newBuilder().setStringVal(value.toString()).build()));
        }
    }

    public void setTime(int index, Time value) throws SQLException {
        if (value == null) {
            setNull(index, Types.TIME);
        } else {
            parameters.put(index, parameter(index, Types.TIME, "TIME",
                    JdbcValue.newBuilder().setStringVal(value.toString()).build()));
        }
    }

    public void setTimestamp(int index, Timestamp value) throws SQLException {
        if (value == null) {
            setNull(index, Types.TIMESTAMP);
        } else {
            parameters.put(index, parameter(index, Types.TIMESTAMP, "TIMESTAMP",
                    JdbcValue.newBuilder().setStringVal(value.toString()).build()));
        }
    }

    public void setObject(int index, Object value) throws SQLException {
        if (value == null) {
            setNull(index, Types.NULL);
        } else if (value instanceof String stringValue) {
            setString(index, stringValue);
        } else if (value instanceof Integer integerValue) {
            setInt(index, integerValue);
        } else if (value instanceof Long longValue) {
            setLong(index, longValue);
        } else if (value instanceof Boolean booleanValue) {
            setBoolean(index, booleanValue);
        } else if (value instanceof Double doubleValue) {
            setDouble(index, doubleValue);
        } else if (value instanceof Float floatValue) {
            setFloat(index, floatValue);
        } else if (value instanceof Short shortValue) {
            setShort(index, shortValue);
        } else if (value instanceof Byte byteValue) {
            setByte(index, byteValue);
        } else if (value instanceof byte[] bytesValue) {
            setBytes(index, bytesValue);
        } else if (value instanceof BigDecimal decimalValue) {
            setBigDecimal(index, decimalValue);
        } else if (value instanceof Date dateValue) {
            setDate(index, dateValue);
        } else if (value instanceof Time timeValue) {
            setTime(index, timeValue);
        } else if (value instanceof Timestamp timestampValue) {
            setTimestamp(index, timestampValue);
        } else if (value instanceof URL urlValue) {
            setURL(index, urlValue);
        } else {
            parameters.put(index, parameter(index, Types.JAVA_OBJECT, value.getClass().getSimpleName(), jdbcValue(value.toString())));
        }
    }

    public void setNull(int index, int sqlType) throws SQLException {
        parameters.put(index, parameter(index, sqlType, "NULL", JdbcValue.newBuilder().setIsNull(true).build()));
    }

    public void setNull(int index, int sqlType, String typeName) throws SQLException {
        parameters.put(index, parameter(index, sqlType, typeName != null ? typeName : "NULL",
                JdbcValue.newBuilder().setIsNull(true).build()));
    }

    public void setObject(int index, Object value, int targetSqlType) throws SQLException {
        if (value == null) {
            setNull(index, targetSqlType);
            return;
        }

        switch (targetSqlType) {
            case Types.VARCHAR, Types.CHAR, Types.LONGVARCHAR, Types.NVARCHAR, Types.NCHAR, Types.LONGNVARCHAR ->
                    parameters.put(index, parameter(index, targetSqlType, "VARCHAR", jdbcValue(String.valueOf(value))));
            case Types.INTEGER ->
                    parameters.put(index, parameter(index, Types.INTEGER, "INTEGER", JdbcValue.newBuilder().setLongVal(((Number) value).intValue()).build()));
            case Types.BIGINT ->
                    parameters.put(index, parameter(index, Types.BIGINT, "BIGINT", JdbcValue.newBuilder().setLongVal(((Number) value).longValue()).build()));
            case Types.SMALLINT ->
                    parameters.put(index, parameter(index, Types.SMALLINT, "SMALLINT", JdbcValue.newBuilder().setLongVal(((Number) value).shortValue()).build()));
            case Types.TINYINT ->
                    parameters.put(index, parameter(index, Types.TINYINT, "TINYINT", JdbcValue.newBuilder().setLongVal(((Number) value).byteValue()).build()));
            case Types.FLOAT, Types.REAL, Types.DOUBLE ->
                    parameters.put(index, parameter(index, targetSqlType, "DOUBLE", JdbcValue.newBuilder().setDoubleVal(((Number) value).doubleValue()).build()));
            case Types.DECIMAL, Types.NUMERIC ->
                    parameters.put(index, parameter(index, targetSqlType, "DECIMAL", JdbcValue.newBuilder().setDecimalVal(String.valueOf(value)).build()));
            case Types.BOOLEAN, Types.BIT ->
                    parameters.put(index, parameter(index, targetSqlType, "BOOLEAN", JdbcValue.newBuilder().setBoolVal((Boolean) value).build()));
            case Types.BINARY, Types.VARBINARY, Types.LONGVARBINARY -> {
                if (value instanceof byte[] bytesValue) {
                    parameters.put(index, parameter(index, targetSqlType, "BINARY", JdbcValue.newBuilder().setBytesVal(com.google.protobuf.ByteString.copyFrom(bytesValue)).build()));
                } else {
                    parameters.put(index, parameter(index, targetSqlType, "BINARY", JdbcValue.newBuilder().setBytesVal(com.google.protobuf.ByteString.copyFrom(String.valueOf(value).getBytes())).build()));
                }
            }
            case Types.DATE -> parameters.put(index, parameter(index, Types.DATE, "DATE", JdbcValue.newBuilder().setStringVal(String.valueOf(value)).build()));
            case Types.TIME -> parameters.put(index, parameter(index, Types.TIME, "TIME", JdbcValue.newBuilder().setStringVal(String.valueOf(value)).build()));
            case Types.TIMESTAMP -> parameters.put(index, parameter(index, Types.TIMESTAMP, "TIMESTAMP", JdbcValue.newBuilder().setStringVal(String.valueOf(value)).build()));
            default -> setObject(index, value);
        }
    }

    public void setObject(int index, Object value, int targetSqlType, int scaleOrLength) throws SQLException {
        setObject(index, value, targetSqlType);
    }

    public void setDate(int index, Date value, Calendar cal) throws SQLException {
        setDate(index, value);
    }

    public void setTime(int index, Time value, Calendar cal) throws SQLException {
        setTime(index, value);
    }

    public void setTimestamp(int index, Timestamp value, Calendar cal) throws SQLException {
        setTimestamp(index, value);
    }

    public ParameterMetaData getParameterMetaData() throws SQLException {
        lifecycle.assertOpen();
        return new MockParameterMetaData(sortedParameters());
    }

    public ResultSetMetaData getMetaData() throws SQLException {
        lifecycle.assertOpen();
        // Metadata is only known at runtime after execution; return null as per JDBC spec.
        return null;
    }

    public void clearParameters() throws SQLException {
        parameters.clear();
    }

    // --- Advanced types ---

    public void setURL(int index, URL value) throws SQLException {
        if (value == null) {
            setNull(index, Types.DATALINK);
        } else {
            parameters.put(index, parameter(index, Types.DATALINK, "DATALINK",
                    JdbcValue.newBuilder().setStringVal(value.toString()).build()));
        }
    }

    public void setBlob(int index, Blob value) throws SQLException {
        if (value == null) {
            setNull(index, Types.BLOB);
        } else {
            try {
                parameters.put(index, parameter(index, Types.BLOB, "BLOB",
                        JdbcValue.newBuilder().setBytesVal(
                                com.google.protobuf.ByteString.copyFrom(value.getBytes(1L, (int) value.length()))
                        ).build()));
            } catch (Exception e) {
                throw new SQLException("Failed to read Blob value", e);
            }
        }
    }

    public void setBlob(int index, InputStream inputStream) throws SQLException {
        if (inputStream == null) {
            setNull(index, Types.BLOB);
        } else {
            try {
                parameters.put(index, parameter(index, Types.BLOB, "BLOB",
                        JdbcValue.newBuilder().setBytesVal(
                                com.google.protobuf.ByteString.readFrom(inputStream)
                        ).build()));
            } catch (Exception e) {
                throw new SQLException("Failed to read Blob InputStream", e);
            }
        }
    }

    public void setBlob(int index, InputStream inputStream, long length) throws SQLException {
        setBlob(index, inputStream);
    }

    public void setClob(int index, Clob value) throws SQLException {
        if (value == null) {
            setNull(index, Types.CLOB);
        } else {
            try {
                parameters.put(index, parameter(index, Types.CLOB, "CLOB",
                        JdbcValue.newBuilder().setStringVal(value.getSubString(1L, (int) value.length())).build()));
            } catch (Exception e) {
                throw new SQLException("Failed to read Clob value", e);
            }
        }
    }

    public void setClob(int index, Reader reader) throws SQLException {
        if (reader == null) {
            setNull(index, Types.CLOB);
        } else {
            try {
                parameters.put(index, parameter(index, Types.CLOB, "CLOB",
                        JdbcValue.newBuilder().setStringVal(new java.io.BufferedReader(reader).lines()
                                .collect(java.util.stream.Collectors.joining("\n"))).build()));
            } catch (Exception e) {
                throw new SQLException("Failed to read Clob Reader", e);
            }
        }
    }

    public void setClob(int index, Reader reader, long length) throws SQLException {
        setClob(index, reader);
    }

    public void setNClob(int index, NClob value) throws SQLException {
        if (value == null) {
            setNull(index, Types.NCLOB);
        } else {
            try {
                parameters.put(index, parameter(index, Types.NCLOB, "NCLOB",
                        JdbcValue.newBuilder().setStringVal(value.getSubString(1L, (int) value.length())).build()));
            } catch (Exception e) {
                throw new SQLException("Failed to read NClob value", e);
            }
        }
    }

    public void setNClob(int index, Reader reader) throws SQLException {
        if (reader == null) {
            setNull(index, Types.NCLOB);
        } else {
            try {
                parameters.put(index, parameter(index, Types.NCLOB, "NCLOB",
                        JdbcValue.newBuilder().setStringVal(new java.io.BufferedReader(reader).lines()
                                .collect(java.util.stream.Collectors.joining("\n"))).build()));
            } catch (Exception e) {
                throw new SQLException("Failed to read NClob Reader", e);
            }
        }
    }

    public void setNClob(int index, Reader reader, long length) throws SQLException {
        setNClob(index, reader);
    }

    public void setArray(int index, Array value) throws SQLException {
        if (value == null) {
            setNull(index, Types.ARRAY);
        } else {
            try {
                parameters.put(index, parameter(index, Types.ARRAY, value.getBaseTypeName(),
                        JdbcValue.newBuilder().setStringVal(value.toString()).build()));
            } catch (Exception e) {
                throw new SQLException("Failed to read Array value", e);
            }
        }
    }

    public void setRef(int index, Ref value) throws SQLException {
        if (value == null) {
            setNull(index, Types.REF);
        } else {
            try {
                parameters.put(index, parameter(index, Types.REF, value.getBaseTypeName(),
                        JdbcValue.newBuilder().setStringVal(value.toString()).build()));
            } catch (Exception e) {
                throw new SQLException("Failed to read Ref value", e);
            }
        }
    }

    public void setRowId(int index, RowId value) throws SQLException {
        if (value == null) {
            setNull(index, Types.ROWID);
        } else {
            parameters.put(index, parameter(index, Types.ROWID, "ROWID",
                    JdbcValue.newBuilder().setBytesVal(
                            com.google.protobuf.ByteString.copyFrom(value.getBytes())
                    ).build()));
        }
    }

    public void setSQLXML(int index, SQLXML value) throws SQLException {
        if (value == null) {
            setNull(index, Types.SQLXML);
        } else {
            try {
                parameters.put(index, parameter(index, Types.SQLXML, "SQLXML",
                        JdbcValue.newBuilder().setStringVal(value.getString()).build()));
            } catch (Exception e) {
                throw new SQLException("Failed to read SQLXML value", e);
            }
        }
    }

    public void setNString(int index, String value) throws SQLException {
        if (value == null) {
            setNull(index, Types.NVARCHAR);
        } else {
            parameters.put(index, parameter(index, Types.NVARCHAR, "NVARCHAR",
                    JdbcValue.newBuilder().setStringVal(value).build()));
        }
    }

    public void setNCharacterStream(int index, Reader reader) throws SQLException {
        setNClob(index, reader);
    }

    public void setNCharacterStream(int index, Reader reader, long length) throws SQLException {
        setNClob(index, reader);
    }

    public void setCharacterStream(int index, Reader reader) throws SQLException {
        setClob(index, reader);
    }

    public void setCharacterStream(int index, Reader reader, int length) throws SQLException {
        setClob(index, reader);
    }

    public void setCharacterStream(int index, Reader reader, long length) throws SQLException {
        setClob(index, reader);
    }

    public void setBinaryStream(int index, InputStream inputStream) throws SQLException {
        setBlob(index, inputStream);
    }

    public void setBinaryStream(int index, InputStream inputStream, int length) throws SQLException {
        setBlob(index, inputStream);
    }

    public void setBinaryStream(int index, InputStream inputStream, long length) throws SQLException {
        setBlob(index, inputStream);
    }

    public void setAsciiStream(int index, InputStream inputStream) throws SQLException {
        if (inputStream == null) {
            setNull(index, Types.LONGVARCHAR);
        } else {
            try {
                parameters.put(index, parameter(index, Types.LONGVARCHAR, "LONGVARCHAR",
                        JdbcValue.newBuilder().setStringVal(
                                new String(inputStream.readAllBytes(), java.nio.charset.StandardCharsets.US_ASCII)
                        ).build()));
            } catch (Exception e) {
                throw new SQLException("Failed to read ASCII stream", e);
            }
        }
    }

    public void setAsciiStream(int index, InputStream inputStream, int length) throws SQLException {
        setAsciiStream(index, inputStream);
    }

    public void setAsciiStream(int index, InputStream inputStream, long length) throws SQLException {
        setAsciiStream(index, inputStream);
    }

    @SuppressWarnings("deprecation")
    public void setUnicodeStream(int index, InputStream inputStream, int length) throws SQLException {
        if (inputStream == null) {
            setNull(index, Types.LONGVARCHAR);
        } else {
            try {
                parameters.put(index, parameter(index, Types.LONGVARCHAR, "LONGVARCHAR",
                        JdbcValue.newBuilder().setStringVal(
                                new String(inputStream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
                        ).build()));
            } catch (Exception e) {
                throw new SQLException("Failed to read Unicode stream", e);
            }
        }
    }

    public ResultSet executeQuery() throws SQLException {
        lifecycle.assertOpen();
        generatedKeys.clearGeneratedKeys();
        var resultSet = ResultSetFactory.create(client.findResultSet(sql, sortedParameters()));
        executionState.storeResultSet(resultSet);
        return resultSet;
    }

    public boolean execute() throws SQLException {
        executeQuery();
        return true;
    }

    public int executeUpdate() throws SQLException {
        lifecycle.assertOpen();
        var resultSet = client.findResultSet(sql, sortedParameters());
        var count = resultSet.getRowsCount();
        executionState.storeUpdateCount(count);
        if (returnGeneratedKeys) {
            generatedKeys.storeGeneratedKeys(resultSet);
        } else {
            generatedKeys.clearGeneratedKeys();
        }
        return count;
    }

    public long executeLargeUpdate() throws SQLException {
        lifecycle.assertOpen();
        var resultSet = client.findResultSet(sql, sortedParameters());
        var count = (long) resultSet.getRowsCount();
        executionState.storeLargeUpdateCount(count);
        if (returnGeneratedKeys) {
            generatedKeys.storeGeneratedKeys(resultSet);
        } else {
            generatedKeys.clearGeneratedKeys();
        }
        return count;
    }

    public ResultSet getResultSet() throws SQLException {
        lifecycle.assertOpen();
        return executionState.getResultSet();
    }

    public int getUpdateCount() throws SQLException {
        lifecycle.assertOpen();
        return executionState.getUpdateCount();
    }

    public long getLargeUpdateCount() throws SQLException {
        lifecycle.assertOpen();
        return executionState.getLargeUpdateCount();
    }

    public boolean getMoreResults() throws SQLException {
        lifecycle.assertOpen();
        return executionState.getMoreResults();
    }

    public boolean getMoreResults(int current) throws SQLException {
        lifecycle.assertOpen();
        return executionState.getMoreResults(current);
    }

    public void closeOnCompletion() throws SQLException {
        lifecycle.assertOpen();
        executionState.closeOnCompletion();
    }

    public boolean isCloseOnCompletion() throws SQLException {
        lifecycle.assertOpen();
        return executionState.isCloseOnCompletion();
    }

    public ResultSet getGeneratedKeys() throws SQLException {
        lifecycle.assertOpen();
        return generatedKeys.getGeneratedKeys();
    }

    public void addBatch() throws SQLException {
        lifecycle.assertOpen();
        parameterBatches.add(sortedParameters());
    }

    public void clearBatch() throws SQLException {
        lifecycle.assertOpen();
        parameterBatches.clear();
    }

    public int[] executeBatch() throws SQLException {
        lifecycle.assertOpen();
        generatedKeys.clearGeneratedKeys();

        var result = new int[parameterBatches.size()];
        for (int i = 0; i < parameterBatches.size(); i++) {
            result[i] = client.findResultSet(sql, parameterBatches.get(i)).getRowsCount();
        }

        parameterBatches.clear();
        executionState.storeUpdateCount(-1);
        return result;
    }

    public long[] executeLargeBatch() throws SQLException {
        lifecycle.assertOpen();
        generatedKeys.clearGeneratedKeys();

        var result = new long[parameterBatches.size()];
        for (int i = 0; i < parameterBatches.size(); i++) {
            result[i] = client.findResultSet(sql, parameterBatches.get(i)).getRowsCount();
        }

        parameterBatches.clear();
        executionState.storeLargeUpdateCount(-1L);
        return result;
    }

    private java.util.List<ParameterMetadata> sortedParameters() {
        var sortedParameters = new ArrayList<>(parameters.entrySet());
        sortedParameters.sort(Comparator.comparingInt(Map.Entry::getKey));
        return sortedParameters.stream().map(Map.Entry::getValue).toList();
    }

    private static ParameterMetadata parameter(int index, int sqlType, String typeName, JdbcValue value) {
        return ParameterMetadata.newBuilder()
                .setIndex(index)
                .setSqlType(sqlType)
                .setTypeName(typeName)
                .setMode(0)
                .setValue(value)
                .build();
    }

    private static JdbcValue jdbcValue(String value) {
        if (value == null) {
            return JdbcValue.newBuilder().setIsNull(true).build();
        }
        return JdbcValue.newBuilder().setStringVal(value).build();
    }
}
