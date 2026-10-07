package io.github.rroyoo.mockjdbc.mock.statement;

import io.github.rroyoo.mockjdbc.mock.ColumnMetadata;
import io.github.rroyoo.mockjdbc.mock.JdbcValue;
import io.github.rroyoo.mockjdbc.mock.SerializedResultSet;

import javax.sql.rowset.RowSetMetaDataImpl;
import javax.sql.rowset.RowSetProvider;
import java.math.BigDecimal;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLDataException;
import java.sql.SQLException;
import java.sql.Time;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Locale;

final class ResultSetFactory {

    private ResultSetFactory() {}

    static ResultSet create(SerializedResultSet serializedResultSet) throws SQLException {
        try {
            var rowSet = RowSetProvider.newFactory().createCachedRowSet();
            rowSet.setMetaData(metaData(serializedResultSet));

            var columnCount = serializedResultSet.getMetadataCount();
            for (var row : serializedResultSet.getRowsList()) {
                if (row.getValuesCount() > columnCount) {
                    throw new SQLException("Row has " + row.getValuesCount() + " values but only "
                            + columnCount + " columns are declared");
                }
                rowSet.moveToInsertRow();
                for (int index = 0; index < columnCount; index++) {
                    var value = index < row.getValuesCount() ? row.getValues(index) : null;
                    var sqlType = serializedResultSet.getMetadata(index).getSqlType();
                    rowSet.updateObject(index + 1, convert(value, sqlType, index + 1));
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

    private static Object convert(JdbcValue value, int sqlType, int columnIndex) throws SQLException {
        if (value == null || isNull(value)) {
            return null;
        }
        try {
            return switch (sqlType) {
                case Types.TINYINT -> (byte) inRange(exactLong(value), Byte.MIN_VALUE, Byte.MAX_VALUE);
                case Types.SMALLINT -> (short) inRange(exactLong(value), Short.MIN_VALUE, Short.MAX_VALUE);
                case Types.INTEGER -> (int) inRange(exactLong(value), Integer.MIN_VALUE, Integer.MAX_VALUE);
                case Types.BIGINT -> exactLong(value);
                case Types.REAL -> (float) toDouble(value);
                case Types.FLOAT, Types.DOUBLE -> toDouble(value);
                case Types.NUMERIC, Types.DECIMAL -> toBigDecimal(value);
                case Types.BOOLEAN, Types.BIT -> toBoolean(value);
                case Types.DATE -> toDate(value);
                case Types.TIME, Types.TIME_WITH_TIMEZONE -> toTime(value);
                case Types.TIMESTAMP, Types.TIMESTAMP_WITH_TIMEZONE -> toTimestamp(value);
                default -> convertByKind(value);
            };
        } catch (RuntimeException e) {
            throw new SQLDataException("Cannot convert value of column " + columnIndex
                    + " to SQL type " + sqlType + ": " + e.getMessage(), e);
        }
    }

    private static boolean isNull(JdbcValue value) {
        return switch (value.getKindCase()) {
            case IS_NULL, KIND_NOT_SET -> true;
            default -> false;
        };
    }

    private static Object convertByKind(JdbcValue value) {
        return switch (value.getKindCase()) {
            case IS_NULL, KIND_NOT_SET -> null;
            case STRING_VAL -> value.getStringVal();
            case LONG_VAL -> value.getLongVal();
            case DOUBLE_VAL -> value.getDoubleVal();
            case BOOL_VAL -> value.getBoolVal();
            case BYTES_VAL -> value.getBytesVal().toByteArray();
            case DECIMAL_VAL -> new BigDecimal(value.getDecimalVal());
            case TIMESTAMP_VAL -> instantOf(value);
        };
    }

    private static long exactLong(JdbcValue value) {
        return switch (value.getKindCase()) {
            case LONG_VAL -> value.getLongVal();
            case DOUBLE_VAL -> BigDecimal.valueOf(value.getDoubleVal()).longValueExact();
            case DECIMAL_VAL -> new BigDecimal(value.getDecimalVal()).longValueExact();
            case STRING_VAL -> new BigDecimal(value.getStringVal().trim()).longValueExact();
            case BOOL_VAL -> value.getBoolVal() ? 1L : 0L;
            default -> throw new IllegalArgumentException("unsupported value kind " + value.getKindCase());
        };
    }

    private static long inRange(long number, long min, long max) {
        if (number < min || number > max) {
            throw new ArithmeticException("value out of range: " + number);
        }
        return number;
    }

    private static double toDouble(JdbcValue value) {
        return switch (value.getKindCase()) {
            case DOUBLE_VAL -> value.getDoubleVal();
            case LONG_VAL -> value.getLongVal();
            case DECIMAL_VAL -> new BigDecimal(value.getDecimalVal()).doubleValue();
            case STRING_VAL -> Double.parseDouble(value.getStringVal().trim());
            default -> throw new IllegalArgumentException("unsupported value kind " + value.getKindCase());
        };
    }

    private static BigDecimal toBigDecimal(JdbcValue value) {
        return switch (value.getKindCase()) {
            case DECIMAL_VAL -> new BigDecimal(value.getDecimalVal());
            case LONG_VAL -> BigDecimal.valueOf(value.getLongVal());
            case DOUBLE_VAL -> BigDecimal.valueOf(value.getDoubleVal());
            case STRING_VAL -> new BigDecimal(value.getStringVal().trim());
            default -> throw new IllegalArgumentException("unsupported value kind " + value.getKindCase());
        };
    }

    private static Boolean toBoolean(JdbcValue value) {
        return switch (value.getKindCase()) {
            case BOOL_VAL -> value.getBoolVal();
            case LONG_VAL -> value.getLongVal() != 0L;
            case STRING_VAL -> parseBoolean(value.getStringVal());
            default -> throw new IllegalArgumentException("unsupported value kind " + value.getKindCase());
        };
    }

    private static boolean parseBoolean(String text) {
        return switch (text.trim().toLowerCase(Locale.ROOT)) {
            case "true", "t", "1", "yes", "y" -> true;
            case "false", "f", "0", "no", "n" -> false;
            default -> throw new IllegalArgumentException("not a boolean: " + text);
        };
    }

    private static Date toDate(JdbcValue value) {
        return switch (value.getKindCase()) {
            case TIMESTAMP_VAL -> Date.valueOf(instantOf(value).toLocalDateTime().toLocalDate());
            case STRING_VAL -> Date.valueOf(LocalDate.parse(datePart(value.getStringVal())));
            default -> throw new IllegalArgumentException("unsupported value kind " + value.getKindCase());
        };
    }

    private static Time toTime(JdbcValue value) {
        return switch (value.getKindCase()) {
            case TIMESTAMP_VAL -> Time.valueOf(instantOf(value).toLocalDateTime().toLocalTime().withNano(0));
            case STRING_VAL -> Time.valueOf(LocalTime.parse(timePart(value.getStringVal())).withNano(0));
            default -> throw new IllegalArgumentException("unsupported value kind " + value.getKindCase());
        };
    }

    private static Timestamp toTimestamp(JdbcValue value) {
        return switch (value.getKindCase()) {
            case TIMESTAMP_VAL -> instantOf(value);
            case STRING_VAL -> parseTimestamp(value.getStringVal().trim());
            default -> throw new IllegalArgumentException("unsupported value kind " + value.getKindCase());
        };
    }

    private static Timestamp parseTimestamp(String text) {
        if (text.contains("T")) {
            return Timestamp.from(Instant.parse(text));
        }
        return Timestamp.valueOf(text);
    }

    private static String datePart(String text) {
        var trimmed = text.trim();
        var separator = firstSeparator(trimmed);
        return separator < 0 ? trimmed : trimmed.substring(0, separator);
    }

    private static String timePart(String text) {
        var trimmed = text.trim();
        var separator = firstSeparator(trimmed);
        return separator < 0 ? trimmed : trimmed.substring(separator + 1);
    }

    private static int firstSeparator(String text) {
        var space = text.indexOf(' ');
        var t = text.indexOf('T');
        if (space < 0) return t;
        return t < 0 ? space : Math.min(space, t);
    }

    private static Timestamp instantOf(JdbcValue value) {
        return Timestamp.from(Instant.ofEpochSecond(value.getTimestampVal().getSeconds(), value.getTimestampVal().getNanos()));
    }

    private static final class ResultSetMetaDataDefaults {
        private static final int NULLABLE_UNKNOWN = 2;

        private ResultSetMetaDataDefaults() {}
    }
}
