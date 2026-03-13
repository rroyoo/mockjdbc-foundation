package io.github.rroyoo.mockjdbc.mock.statement;

import io.github.rroyoo.mockjdbc.mock.ColumnMetadata;
import io.github.rroyoo.mockjdbc.mock.JdbcValue;
import io.github.rroyoo.mockjdbc.mock.SerializedResultSet;

import javax.sql.rowset.RowSetMetaDataImpl;
import javax.sql.rowset.RowSetProvider;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;

final class ResultSetFactory {

    private ResultSetFactory() {}

    static ResultSet create(SerializedResultSet serializedResultSet) throws SQLException {
        try {
            var rowSet = RowSetProvider.newFactory().createCachedRowSet();
            rowSet.setMetaData(metaData(serializedResultSet));

            for (var row : serializedResultSet.getRowsList()) {
                rowSet.moveToInsertRow();
                for (int columnIndex = 0; columnIndex < row.getValuesCount(); columnIndex++) {
                    rowSet.updateObject(columnIndex + 1, convert(row.getValues(columnIndex)));
                }
                rowSet.insertRow();
            }

            rowSet.moveToCurrentRow();
            rowSet.beforeFirst();
            return rowSet;
        } catch (SQLException e) {
            throw e;
        } catch (Exception e) {
            throw new SQLException("Failed to create cached ResultSet", e);
        }
    }

    private static RowSetMetaDataImpl metaData(SerializedResultSet serializedResultSet) throws SQLException {
        var metadata = new RowSetMetaDataImpl();
        metadata.setColumnCount(serializedResultSet.getMetadataCount());

        for (int index = 0; index < serializedResultSet.getMetadataCount(); index++) {
            var columnIndex = index + 1;
            var column = serializedResultSet.getMetadata(index);
            configureColumn(metadata, columnIndex, column);
        }

        return metadata;
    }

    private static void configureColumn(RowSetMetaDataImpl metadata, int columnIndex, ColumnMetadata column) throws SQLException {
        metadata.setAutoIncrement(columnIndex, false);
        metadata.setCaseSensitive(columnIndex, true);
        metadata.setSearchable(columnIndex, true);
        metadata.setCurrency(columnIndex, false);
        metadata.setNullable(columnIndex, ResultSetMetaDataDefaults.NULLABLE_UNKNOWN);
        metadata.setSigned(columnIndex, isSigned(column.getSqlType()));
        metadata.setColumnDisplaySize(columnIndex, Math.max(column.getLabel().length(), column.getName().length()));
        metadata.setColumnLabel(columnIndex, column.getLabel().isBlank() ? column.getName() : column.getLabel());
        metadata.setColumnName(columnIndex, column.getName().isBlank() ? column.getLabel() : column.getName());
        metadata.setSchemaName(columnIndex, "");
        metadata.setPrecision(columnIndex, 0);
        metadata.setScale(columnIndex, 0);
        metadata.setTableName(columnIndex, "");
        metadata.setCatalogName(columnIndex, "");
        metadata.setColumnType(columnIndex, column.getSqlType());
        metadata.setColumnTypeName(columnIndex, column.getTypeName());
    }

    private static boolean isSigned(int sqlType) {
        return switch (sqlType) {
            case java.sql.Types.TINYINT,
                 java.sql.Types.SMALLINT,
                 java.sql.Types.INTEGER,
                 java.sql.Types.BIGINT,
                 java.sql.Types.FLOAT,
                 java.sql.Types.REAL,
                 java.sql.Types.DOUBLE,
                 java.sql.Types.NUMERIC,
                 java.sql.Types.DECIMAL -> true;
            default -> false;
        };
    }

    private static Object convert(JdbcValue value) {
        return switch (value.getKindCase()) {
            case IS_NULL, KIND_NOT_SET -> null;
            case STRING_VAL -> value.getStringVal();
            case LONG_VAL -> value.getLongVal();
            case DOUBLE_VAL -> value.getDoubleVal();
            case BOOL_VAL -> value.getBoolVal();
            case BYTES_VAL -> value.getBytesVal().toByteArray();
            case DECIMAL_VAL -> new BigDecimal(value.getDecimalVal());
            case TIMESTAMP_VAL -> Timestamp.from(Instant.ofEpochSecond(value.getTimestampVal().getSeconds(), value.getTimestampVal().getNanos()));
        };
    }

    private static final class ResultSetMetaDataDefaults {
        private static final int NULLABLE_UNKNOWN = 2;

        private ResultSetMetaDataDefaults() {}
    }
}
