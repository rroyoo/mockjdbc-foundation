package io.github.rroyoo.mockjdbc.mock.resultset;

import io.github.rroyoo.mockjdbc.mock.ColumnMetadata;

import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.List;

/**
 * Mock implementation of ResultSetMetaData backed by protobuf ColumnMetadata.
 */
final class MockResultSetMetaData implements ResultSetMetaData {

    private final List<ColumnMetadata> metadata;

    MockResultSetMetaData(List<ColumnMetadata> metadata) {
        this.metadata = metadata;
    }

    @Override
    public int getColumnCount() throws SQLException {
        return metadata.size();
    }

    @Override
    public String getColumnName(int column) throws SQLException {
        return getColumnMetadata(column).getName();
    }

    @Override
    public String getColumnLabel(int column) throws SQLException {
        return getColumnMetadata(column).getLabel();
    }

    @Override
    public int getColumnType(int column) throws SQLException {
        return getColumnMetadata(column).getSqlType();
    }

    @Override
    public String getColumnTypeName(int column) throws SQLException {
        return getColumnMetadata(column).getTypeName();
    }

    @Override
    public boolean isAutoIncrement(int column) throws SQLException {
        return false;
    }

    @Override
    public boolean isCaseSensitive(int column) throws SQLException {
        return true;
    }

    @Override
    public boolean isSearchable(int column) throws SQLException {
        return true;
    }

    @Override
    public boolean isCurrency(int column) throws SQLException {
        return false;
    }

    @Override
    public int isNullable(int column) throws SQLException {
        return ResultSetMetaData.columnNullableUnknown;
    }

    @Override
    public boolean isSigned(int column) throws SQLException {
        return false;
    }

    @Override
    public int getColumnDisplaySize(int column) throws SQLException {
        return 0;
    }

    @Override
    public String getSchemaName(int column) throws SQLException {
        return "";
    }

    @Override
    public int getPrecision(int column) throws SQLException {
        return 0;
    }

    @Override
    public int getScale(int column) throws SQLException {
        return 0;
    }

    @Override
    public String getTableName(int column) throws SQLException {
        return "";
    }

    @Override
    public String getCatalogName(int column) throws SQLException {
        return "";
    }

    @Override
    public boolean isReadOnly(int column) throws SQLException {
        return true;
    }

    @Override
    public boolean isWritable(int column) throws SQLException {
        return false;
    }

    @Override
    public boolean isDefinitelyWritable(int column) throws SQLException {
        return false;
    }

    @Override
    public String getColumnClassName(int column) throws SQLException {
        return Object.class.getName();
    }

    @Override
    public <T> T unwrap(Class<T> iface) throws SQLException {
        return null;
    }

    @Override
    public boolean isWrapperFor(Class<?> iface) throws SQLException {
        return false;
    }

    private ColumnMetadata getColumnMetadata(int column) throws SQLException {
        if (column < 1 || column > metadata.size()) {
            throw new SQLException("Invalid column index: " + column);
        }
        return metadata.get(column - 1); // 0-based in list
    }
}

