package io.github.rroyoo.mockjdbc.mock.statement;

import io.github.rroyoo.mockjdbc.mock.ColumnMetadata;
import io.github.rroyoo.mockjdbc.mock.JdbcValue;
import io.github.rroyoo.mockjdbc.mock.Row;
import io.github.rroyoo.mockjdbc.mock.SerializedResultSet;

import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

final class ResultSetCursorHandler {

    private final ResultSetLifecycleHandler lifecycle;
    private final List<Row> rows;
    private final Map<String, Integer> columnIndexes;
    private final AtomicInteger cursor = new AtomicInteger(-1);

    ResultSetCursorHandler(ResultSetLifecycleHandler lifecycle, SerializedResultSet resultSet) {
        this.lifecycle = lifecycle;
        this.rows = resultSet.getRowsList();
        this.columnIndexes = buildColumnIndexes(resultSet.getMetadataList());
    }

    public boolean next() throws SQLException {
        lifecycle.assertOpen();
        var nextIndex = cursor.incrementAndGet();
        return nextIndex < rows.size();
    }

    public Object getObject(int columnIndex) throws SQLException {
        return currentValue(columnIndex);
    }

    public Object getObject(String columnLabel) throws SQLException {
        return currentValue(findColumn(columnLabel));
    }

    public String getString(int columnIndex) throws SQLException {
        var value = currentValue(columnIndex);
        return value == null ? null : value.toString();
    }

    public String getString(String columnLabel) throws SQLException {
        var value = currentValue(findColumn(columnLabel));
        return value == null ? null : value.toString();
    }

    public int findColumn(String columnLabel) throws SQLException {
        lifecycle.assertOpen();
        var columnIndex = columnIndexes.get(columnLabel);
        if (columnIndex == null) {
            throw new SQLException("Unknown column label: " + columnLabel);
        }
        return columnIndex;
    }

    private Object currentValue(int columnIndex) throws SQLException {
        lifecycle.assertOpen();
        var rowIndex = cursor.get();
        if (rowIndex < 0 || rowIndex >= rows.size()) {
            throw new SQLException("Cursor is not positioned on a row");
        }
        var row = rows.get(rowIndex);
        if (columnIndex < 1 || columnIndex > row.getValuesCount()) {
            throw new SQLException("Invalid column index: " + columnIndex);
        }
        return convert(row.getValues(columnIndex - 1));
    }

    private static Map<String, Integer> buildColumnIndexes(List<ColumnMetadata> metadata) {
        var indexes = new HashMap<String, Integer>();
        for (int i = 0; i < metadata.size(); i++) {
            var columnIndex = i + 1;
            var column = metadata.get(i);
            if (!column.getLabel().isBlank()) {
                indexes.put(column.getLabel(), columnIndex);
            }
            if (!column.getName().isBlank()) {
                indexes.putIfAbsent(column.getName(), columnIndex);
            }
        }
        return indexes;
    }

    private static Object convert(JdbcValue value) {
        return switch (value.getKindCase()) {
            case IS_NULL, KIND_NOT_SET -> null;
            case STRING_VAL -> value.getStringVal();
            case LONG_VAL -> value.getLongVal();
            case DOUBLE_VAL -> value.getDoubleVal();
            case BOOL_VAL -> value.getBoolVal();
            case BYTES_VAL -> value.getBytesVal().toByteArray();
            case DECIMAL_VAL -> new java.math.BigDecimal(value.getDecimalVal());
            case TIMESTAMP_VAL -> Timestamp.from(Instant.ofEpochSecond(value.getTimestampVal().getSeconds(), value.getTimestampVal().getNanos()));
        };
    }
}

