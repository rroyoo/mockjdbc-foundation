package io.github.rroyoo.mockjdbc.mock.resultset;

import io.github.rroyoo.mockjdbc.mock.ColumnMetadata;
import io.github.rroyoo.mockjdbc.mock.JdbcValue;
import io.github.rroyoo.mockjdbc.mock.Row;
import io.github.rroyoo.mockjdbc.mock.SerializedResultSet;

import java.io.InputStream;
import java.io.Reader;
import java.math.BigDecimal;
import java.net.URL;
import java.sql.*;
import java.util.Calendar;
import java.util.List;
import java.util.Map;

/**
 * Mock implementation of JDBC ResultSet backed by protobuf SerializedResultSet.
 *
 * Provides navigation through rows and column value access based on the
 * data received from the gRPC MockQueryService.
 */
final class MockResultSet implements ResultSet {

    private final List<ColumnMetadata> metadata;
    private final List<Row> rows;
    private int currentRowIndex = -1; // Before first row
    private boolean wasNull = false;

    MockResultSet(SerializedResultSet serializedResultSet) {
        this.metadata = serializedResultSet.getMetadataList();
        this.rows = serializedResultSet.getRowsList();
    }

    @Override
    public boolean next() throws SQLException {
        if (currentRowIndex < rows.size() - 1) {
            currentRowIndex++;
            return true;
        }
        return false;
    }

    @Override
    public void close() throws SQLException {
        // No resources to close for in-memory result set
    }

    @Override
    public boolean wasNull() throws SQLException {
        return wasNull;
    }

    @Override
    public String getString(int columnIndex) throws SQLException {
        JdbcValue value = getValue(columnIndex);
        if (value.getKindCase() == JdbcValue.KindCase.STRING_VAL) {
            wasNull = false;
            return value.getStringVal();
        }
        wasNull = true;
        return null;
    }

    @Override
    public boolean getBoolean(int columnIndex) throws SQLException {
        JdbcValue value = getValue(columnIndex);
        if (value.getKindCase() == JdbcValue.KindCase.BOOL_VAL) {
            wasNull = false;
            return value.getBoolVal();
        }
        wasNull = true;
        return false;
    }

    @Override
    public byte getByte(int columnIndex) throws SQLException {
        return (byte) getInt(columnIndex);
    }

    @Override
    public short getShort(int columnIndex) throws SQLException {
        return (short) getInt(columnIndex);
    }

    @Override
    public int getInt(int columnIndex) throws SQLException {
        JdbcValue value = getValue(columnIndex);
        if (value.getKindCase() == JdbcValue.KindCase.LONG_VAL) {
            wasNull = false;
            return (int) value.getLongVal();
        }
        wasNull = true;
        return 0;
    }

    @Override
    public long getLong(int columnIndex) throws SQLException {
        JdbcValue value = getValue(columnIndex);
        if (value.getKindCase() == JdbcValue.KindCase.LONG_VAL) {
            wasNull = false;
            return value.getLongVal();
        }
        wasNull = true;
        return 0L;
    }

    @Override
    public float getFloat(int columnIndex) throws SQLException {
        JdbcValue value = getValue(columnIndex);
        if (value.getKindCase() == JdbcValue.KindCase.DOUBLE_VAL) {
            wasNull = false;
            return (float) value.getDoubleVal();
        }
        wasNull = true;
        return 0.0f;
    }

    @Override
    public double getDouble(int columnIndex) throws SQLException {
        JdbcValue value = getValue(columnIndex);
        if (value.getKindCase() == JdbcValue.KindCase.DOUBLE_VAL) {
            wasNull = false;
            return value.getDoubleVal();
        }
        wasNull = true;
        return 0.0;
    }

    @Override
    public BigDecimal getBigDecimal(int columnIndex, int scale) throws SQLException {
        return getBigDecimal(columnIndex);
    }

    @Override
    public byte[] getBytes(int columnIndex) throws SQLException {
        JdbcValue value = getValue(columnIndex);
        if (value.getKindCase() == JdbcValue.KindCase.BYTES_VAL) {
            wasNull = false;
            return value.getBytesVal().toByteArray();
        }
        wasNull = true;
        return null;
    }

    @Override
    public Date getDate(int columnIndex) throws SQLException {
        String dateString = getString(columnIndex);
        if (dateString != null) {
            return Date.valueOf(dateString);
        }
        return null;
    }

    @Override
    public Time getTime(int columnIndex) throws SQLException {
        String timeString = getString(columnIndex);
        if (timeString != null) {
            return Time.valueOf(timeString);
        }
        return null;
    }

    @Override
    public Timestamp getTimestamp(int columnIndex) throws SQLException {
        String timestampString = getString(columnIndex);
        if (timestampString != null) {
            return Timestamp.valueOf(timestampString);
        }
        return null;
    }

    @Override
    public String getString(String columnLabel) throws SQLException {
        return getString(findColumn(columnLabel));
    }

    @Override
    public boolean getBoolean(String columnLabel) throws SQLException {
        return getBoolean(findColumn(columnLabel));
    }

    @Override
    public byte getByte(String columnLabel) throws SQLException {
        return getByte(findColumn(columnLabel));
    }

    @Override
    public short getShort(String columnLabel) throws SQLException {
        return getShort(findColumn(columnLabel));
    }

    @Override
    public int getInt(String columnLabel) throws SQLException {
        return getInt(findColumn(columnLabel));
    }

    @Override
    public long getLong(String columnLabel) throws SQLException {
        return getLong(findColumn(columnLabel));
    }

    @Override
    public float getFloat(String columnLabel) throws SQLException {
        return getFloat(findColumn(columnLabel));
    }

    @Override
    public double getDouble(String columnLabel) throws SQLException {
        return getDouble(findColumn(columnLabel));
    }

    @Override
    public BigDecimal getBigDecimal(String columnLabel, int scale) throws SQLException {
        return getBigDecimal(findColumn(columnLabel), scale);
    }

    @Override
    public byte[] getBytes(String columnLabel) throws SQLException {
        return getBytes(findColumn(columnLabel));
    }

    @Override
    public Date getDate(String columnLabel) throws SQLException {
        return getDate(findColumn(columnLabel));
    }

    @Override
    public Time getTime(String columnLabel) throws SQLException {
        return getTime(findColumn(columnLabel));
    }

    @Override
    public Timestamp getTimestamp(String columnLabel) throws SQLException {
        return getTimestamp(findColumn(columnLabel));
    }

    @Override
    public int findColumn(String columnLabel) throws SQLException {
        for (int i = 0; i < metadata.size(); i++) {
            if (metadata.get(i).getName().equalsIgnoreCase(columnLabel)) {
                return i + 1; // JDBC columns are 1-based
            }
        }
        throw new SQLException("Column not found: " + columnLabel);
    }

    @Override
    public ResultSetMetaData getMetaData() throws SQLException {
        return new MockResultSetMetaData(metadata);
    }

    @Override
    public Object getObject(int columnIndex) throws SQLException {
        JdbcValue value = getValue(columnIndex);

        switch (value.getKindCase()) {
            case STRING_VAL: return value.getStringVal();
            case LONG_VAL: return value.getLongVal();
            case DOUBLE_VAL: return value.getDoubleVal();
            case BOOL_VAL: return value.getBoolVal();
            case BYTES_VAL: return value.getBytesVal().toByteArray();
            case DECIMAL_VAL: return new BigDecimal(value.getDecimalVal());
            case IS_NULL:
            case KIND_NOT_SET:
            default:
                wasNull = true;
                return null;
        }
    }

    @Override
    public Object getObject(String columnLabel) throws SQLException {
        return getObject(findColumn(columnLabel));
    }

    @Override
    public BigDecimal getBigDecimal(int columnIndex) throws SQLException {
        String value = getString(columnIndex);
        if (value != null) {
            wasNull = false;
            return new BigDecimal(value);
        }
        wasNull = true;
        return null;
    }

    @Override
    public BigDecimal getBigDecimal(String columnLabel) throws SQLException {
        return getBigDecimal(findColumn(columnLabel));
    }

    @Override
    public boolean isBeforeFirst() throws SQLException {
        return currentRowIndex < 0 && !rows.isEmpty();
    }

    @Override
    public boolean isAfterLast() throws SQLException {
        return currentRowIndex >= rows.size() && !rows.isEmpty();
    }

    @Override
    public boolean isFirst() throws SQLException {
        return currentRowIndex == 0;
    }

    @Override
    public boolean isLast() throws SQLException {
        return currentRowIndex == rows.size() - 1 && !rows.isEmpty();
    }

    @Override
    public int getRow() throws SQLException {
        return currentRowIndex + 1; // JDBC rows are 1-based
    }

    // Helper method to get value from current row
    private JdbcValue getValue(int columnIndex) throws SQLException {
        if (currentRowIndex < 0) {
            throw new SQLException("Before first row");
        }
        if (currentRowIndex >= rows.size()) {
            throw new SQLException("After last row");
        }
        if (columnIndex < 1 || columnIndex > metadata.size()) {
            throw new SQLException("Invalid column index: " + columnIndex);
        }

        Row currentRow = rows.get(currentRowIndex);
        return currentRow.getValues(columnIndex - 1); // 0-based in protobuf
    }

    // Stub implementations for unsupported operations
    @Override public InputStream getAsciiStream(int columnIndex) throws SQLException { return null; }
    @Override public InputStream getUnicodeStream(int columnIndex) throws SQLException { return null; }
    @Override public InputStream getBinaryStream(int columnIndex) throws SQLException { return null; }
    @Override public InputStream getAsciiStream(String columnLabel) throws SQLException { return null; }
    @Override public InputStream getUnicodeStream(String columnLabel) throws SQLException { return null; }
    @Override public InputStream getBinaryStream(String columnLabel) throws SQLException { return null; }
    @Override public SQLWarning getWarnings() throws SQLException { return null; }
    @Override public void clearWarnings() throws SQLException {}
    @Override public String getCursorName() throws SQLException { return null; }
    @Override public Reader getCharacterStream(int columnIndex) throws SQLException { return null; }
    @Override public Reader getCharacterStream(String columnLabel) throws SQLException { return null; }
    @Override public void beforeFirst() throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void afterLast() throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public boolean first() throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public boolean last() throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public boolean absolute(int row) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public boolean relative(int rows) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public boolean previous() throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void setFetchDirection(int direction) throws SQLException {}
    @Override public int getFetchDirection() throws SQLException { return ResultSet.FETCH_FORWARD; }
    @Override public void setFetchSize(int rows) throws SQLException {}
    @Override public int getFetchSize() throws SQLException { return 0; }
    @Override public int getType() throws SQLException { return ResultSet.TYPE_FORWARD_ONLY; }
    @Override public int getConcurrency() throws SQLException { return ResultSet.CONCUR_READ_ONLY; }
    @Override public boolean rowUpdated() throws SQLException { return false; }
    @Override public boolean rowInserted() throws SQLException { return false; }
    @Override public boolean rowDeleted() throws SQLException { return false; }
    @Override public void updateNull(int columnIndex) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateBoolean(int columnIndex, boolean x) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateByte(int columnIndex, byte x) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateShort(int columnIndex, short x) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateInt(int columnIndex, int x) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateLong(int columnIndex, long x) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateFloat(int columnIndex, float x) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateDouble(int columnIndex, double x) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateBigDecimal(int columnIndex, BigDecimal x) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateString(int columnIndex, String x) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateBytes(int columnIndex, byte[] x) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateDate(int columnIndex, Date x) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateTime(int columnIndex, Time x) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateTimestamp(int columnIndex, Timestamp x) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateAsciiStream(int columnIndex, InputStream x, int length) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateBinaryStream(int columnIndex, InputStream x, int length) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateCharacterStream(int columnIndex, Reader x, int length) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateObject(int columnIndex, Object x, int scaleOrLength) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateObject(int columnIndex, Object x) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateNull(String columnLabel) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateBoolean(String columnLabel, boolean x) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateByte(String columnLabel, byte x) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateShort(String columnLabel, short x) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateInt(String columnLabel, int x) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateLong(String columnLabel, long x) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateFloat(String columnLabel, float x) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateDouble(String columnLabel, double x) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateBigDecimal(String columnLabel, BigDecimal x) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateString(String columnLabel, String x) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateBytes(String columnLabel, byte[] x) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateDate(String columnLabel, Date x) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateTime(String columnLabel, Time x) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateTimestamp(String columnLabel, Timestamp x) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateAsciiStream(String columnLabel, InputStream x, int length) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateBinaryStream(String columnLabel, InputStream x, int length) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateCharacterStream(String columnLabel, Reader reader, int length) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateObject(String columnLabel, Object x, int scaleOrLength) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateObject(String columnLabel, Object x) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void insertRow() throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateRow() throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void deleteRow() throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void refreshRow() throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void cancelRowUpdates() throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void moveToInsertRow() throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void moveToCurrentRow() throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public Statement getStatement() throws SQLException { return null; }
    @Override public Object getObject(int columnIndex, Map<String, Class<?>> map) throws SQLException { return getObject(columnIndex); }
    @Override public Ref getRef(int columnIndex) throws SQLException { return null; }
    @Override public Blob getBlob(int columnIndex) throws SQLException { return null; }
    @Override public Clob getClob(int columnIndex) throws SQLException { return null; }
    @Override public Array getArray(int columnIndex) throws SQLException { return null; }
    @Override public Object getObject(String columnLabel, Map<String, Class<?>> map) throws SQLException { return getObject(columnLabel); }
    @Override public Ref getRef(String columnLabel) throws SQLException { return null; }
    @Override public Blob getBlob(String columnLabel) throws SQLException { return null; }
    @Override public Clob getClob(String columnLabel) throws SQLException { return null; }
    @Override public Array getArray(String columnLabel) throws SQLException { return null; }
    @Override public Date getDate(int columnIndex, Calendar cal) throws SQLException { return getDate(columnIndex); }
    @Override public Date getDate(String columnLabel, Calendar cal) throws SQLException { return getDate(columnLabel); }
    @Override public Time getTime(int columnIndex, Calendar cal) throws SQLException { return getTime(columnIndex); }
    @Override public Time getTime(String columnLabel, Calendar cal) throws SQLException { return getTime(columnLabel); }
    @Override public Timestamp getTimestamp(int columnIndex, Calendar cal) throws SQLException { return getTimestamp(columnIndex); }
    @Override public Timestamp getTimestamp(String columnLabel, Calendar cal) throws SQLException { return getTimestamp(columnLabel); }
    @Override public URL getURL(int columnIndex) throws SQLException { return null; }
    @Override public URL getURL(String columnLabel) throws SQLException { return null; }
    @Override public void updateRef(int columnIndex, Ref x) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateRef(String columnLabel, Ref x) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateBlob(int columnIndex, Blob x) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateBlob(String columnLabel, Blob x) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateClob(int columnIndex, Clob x) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateClob(String columnLabel, Clob x) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateArray(int columnIndex, Array x) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateArray(String columnLabel, Array x) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public RowId getRowId(int columnIndex) throws SQLException { return null; }
    @Override public RowId getRowId(String columnLabel) throws SQLException { return null; }
    @Override public void updateRowId(int columnIndex, RowId x) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateRowId(String columnLabel, RowId x) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public int getHoldability() throws SQLException { return ResultSet.CLOSE_CURSORS_AT_COMMIT; }
    @Override public boolean isClosed() throws SQLException { return false; }
    @Override public void updateNString(int columnIndex, String nString) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateNString(String columnLabel, String nString) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateNClob(int columnIndex, NClob nClob) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateNClob(String columnLabel, NClob nClob) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public NClob getNClob(int columnIndex) throws SQLException { return null; }
    @Override public NClob getNClob(String columnLabel) throws SQLException { return null; }
    @Override public SQLXML getSQLXML(int columnIndex) throws SQLException { return null; }
    @Override public SQLXML getSQLXML(String columnLabel) throws SQLException { return null; }
    @Override public void updateSQLXML(int columnIndex, SQLXML xmlObject) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateSQLXML(String columnLabel, SQLXML xmlObject) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public String getNString(int columnIndex) throws SQLException { return getString(columnIndex); }
    @Override public String getNString(String columnLabel) throws SQLException { return getString(columnLabel); }
    @Override public Reader getNCharacterStream(int columnIndex) throws SQLException { return null; }
    @Override public Reader getNCharacterStream(String columnLabel) throws SQLException { return null; }
    @Override public void updateNCharacterStream(int columnIndex, Reader x, long length) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateNCharacterStream(String columnLabel, Reader reader, long length) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateAsciiStream(int columnIndex, InputStream x, long length) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateBinaryStream(int columnIndex, InputStream x, long length) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateCharacterStream(int columnIndex, Reader x, long length) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateAsciiStream(String columnLabel, InputStream x, long length) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateBinaryStream(String columnLabel, InputStream x, long length) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateCharacterStream(String columnLabel, Reader reader, long length) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateBlob(int columnIndex, InputStream inputStream, long length) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateBlob(String columnLabel, InputStream inputStream, long length) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateClob(int columnIndex, Reader reader, long length) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateClob(String columnLabel, Reader reader, long length) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateNClob(int columnIndex, Reader reader, long length) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateNClob(String columnLabel, Reader reader, long length) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateNCharacterStream(int columnIndex, Reader x) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateNCharacterStream(String columnLabel, Reader reader) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateAsciiStream(int columnIndex, InputStream x) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateBinaryStream(int columnIndex, InputStream x) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateCharacterStream(int columnIndex, Reader x) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateAsciiStream(String columnLabel, InputStream x) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateBinaryStream(String columnLabel, InputStream x) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateCharacterStream(String columnLabel, Reader reader) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateBlob(int columnIndex, InputStream inputStream) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateBlob(String columnLabel, InputStream inputStream) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateClob(int columnIndex, Reader reader) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateClob(String columnLabel, Reader reader) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateNClob(int columnIndex, Reader reader) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateNClob(String columnLabel, Reader reader) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public <T> T getObject(int columnIndex, Class<T> type) throws SQLException { return null; }
    @Override public <T> T getObject(String columnLabel, Class<T> type) throws SQLException { return null; }
    @Override public <T> T unwrap(Class<T> iface) throws SQLException { return null; }
    @Override public boolean isWrapperFor(Class<?> iface) throws SQLException { return false; }
}

