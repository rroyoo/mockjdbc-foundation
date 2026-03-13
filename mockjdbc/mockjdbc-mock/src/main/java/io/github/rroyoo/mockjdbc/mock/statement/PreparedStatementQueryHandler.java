package io.github.rroyoo.mockjdbc.mock.statement;

import io.github.rroyoo.mockjdbc.mock.JdbcValue;
import io.github.rroyoo.mockjdbc.mock.ParameterMetadata;
import io.github.rroyoo.mockjdbc.mock.driver.MockConfig;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.ParameterMetaData;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
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
            parameters.put(index, parameter(index, Types.DOUBLE, "DOUBLE", JdbcValue.newBuilder().setDoubleVal(doubleValue).build()));
        } else if (value instanceof byte[] bytesValue) {
            parameters.put(index, parameter(index, Types.BINARY, "BINARY", JdbcValue.newBuilder().setBytesVal(com.google.protobuf.ByteString.copyFrom(bytesValue)).build()));
        } else if (value instanceof BigDecimal decimalValue) {
            parameters.put(index, parameter(index, Types.DECIMAL, "DECIMAL", JdbcValue.newBuilder().setDecimalVal(decimalValue.toPlainString()).build()));
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
        } else {
            setObject(index, value);
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
