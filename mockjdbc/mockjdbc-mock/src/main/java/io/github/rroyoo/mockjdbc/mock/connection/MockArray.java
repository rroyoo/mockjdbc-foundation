package io.github.rroyoo.mockjdbc.mock.connection;

import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.Types;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;

final class MockArray implements Array {

    private static final Map<String, Integer> BASE_TYPES =
            Map.ofEntries(
                    Map.entry("VARCHAR", Types.VARCHAR),
                    Map.entry("CHAR", Types.CHAR),
                    Map.entry("INTEGER", Types.INTEGER),
                    Map.entry("INT", Types.INTEGER),
                    Map.entry("BIGINT", Types.BIGINT),
                    Map.entry("SMALLINT", Types.SMALLINT),
                    Map.entry("BOOLEAN", Types.BOOLEAN),
                    Map.entry("DOUBLE", Types.DOUBLE),
                    Map.entry("DECIMAL", Types.DECIMAL),
                    Map.entry("NUMERIC", Types.NUMERIC),
                    Map.entry("DATE", Types.DATE),
                    Map.entry("TIME", Types.TIME),
                    Map.entry("TIMESTAMP", Types.TIMESTAMP));

    private final String baseTypeName;
    private final Object[] elements;
    private boolean freed;

    MockArray(String baseTypeName, Object[] elements) {
        this.baseTypeName = baseTypeName;
        this.elements = elements.clone();
    }

    @Override
    public String getBaseTypeName() throws SQLException {
        ensureValid();
        return baseTypeName;
    }

    @Override
    public int getBaseType() throws SQLException {
        ensureValid();
        return BASE_TYPES.getOrDefault(baseTypeName.toUpperCase(Locale.ROOT), Types.OTHER);
    }

    @Override
    public Object getArray() throws SQLException {
        ensureValid();
        return elements.clone();
    }

    @Override
    public Object getArray(Map<String, Class<?>> map) throws SQLException {
        return getArray();
    }

    @Override
    public Object getArray(long index, int count) throws SQLException {
        ensureValid();
        if (index < 1
                || count < 0
                || index > elements.length
                || count > elements.length - (index - 1)) {
            throw new SQLException("Invalid array slice: index=" + index + ", count=" + count);
        }
        return Arrays.copyOfRange(elements, (int) index - 1, (int) index - 1 + count);
    }

    @Override
    public Object getArray(long index, int count, Map<String, Class<?>> map) throws SQLException {
        return getArray(index, count);
    }

    @Override
    public ResultSet getResultSet() throws SQLException {
        ensureValid();
        throw new SQLFeatureNotSupportedException(
                "Array.getResultSet is not supported by MockJDBC");
    }

    @Override
    public ResultSet getResultSet(Map<String, Class<?>> map) throws SQLException {
        return getResultSet();
    }

    @Override
    public ResultSet getResultSet(long index, int count) throws SQLException {
        return getResultSet();
    }

    @Override
    public ResultSet getResultSet(long index, int count, Map<String, Class<?>> map)
            throws SQLException {
        return getResultSet();
    }

    @Override
    public void free() {
        freed = true;
    }

    @Override
    public String toString() {
        return baseTypeName + Arrays.toString(elements);
    }

    private void ensureValid() throws SQLException {
        if (freed) {
            throw new SQLException("Array has been freed");
        }
    }
}
