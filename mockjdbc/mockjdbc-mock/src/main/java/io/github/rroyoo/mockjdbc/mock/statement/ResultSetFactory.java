package io.github.rroyoo.mockjdbc.mock.statement;

import io.github.rroyoo.mockjdbc.mock.ColumnMetadata;
import io.github.rroyoo.mockjdbc.mock.JdbcValue;
import io.github.rroyoo.mockjdbc.mock.SerializedResultSet;
import java.math.BigDecimal;
import java.sql.Blob;
import java.sql.Clob;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLDataException;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.Time;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.Set;
import javax.sql.rowset.RowSetMetaDataImpl;
import javax.sql.rowset.RowSetProvider;
import javax.sql.rowset.serial.SerialBlob;
import javax.sql.rowset.serial.SerialClob;

final class ResultSetFactory {

    private static final Set<Integer> SIGNED_SQL_TYPES =
            Set.of(
                    Types.TINYINT,
                    Types.SMALLINT,
                    Types.INTEGER,
                    Types.BIGINT,
                    Types.FLOAT,
                    Types.REAL,
                    Types.DOUBLE,
                    Types.NUMERIC,
                    Types.DECIMAL);
    private static final Set<String> TRUE_VALUES = Set.of("true", "t", "1", "yes", "y");
    private static final Set<String> FALSE_VALUES = Set.of("false", "f", "0", "no", "n");

    private ResultSetFactory() {}

    static ResultSet create(SerializedResultSet serializedResultSet) throws SQLException {
        try {
            var rowSet = RowSetProvider.newFactory().createCachedRowSet();
            rowSet.setMetaData(metaData(serializedResultSet));

            var columnCount = serializedResultSet.getMetadataCount();
            for (var row : serializedResultSet.getRowsList()) {
                insertRow(rowSet, serializedResultSet, row, columnCount);
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

    private static void insertRow(
            javax.sql.rowset.CachedRowSet rowSet,
            SerializedResultSet serializedResultSet,
            io.github.rroyoo.mockjdbc.mock.Row row,
            int columnCount)
            throws SQLException {
        if (row.getValuesCount() > columnCount) {
            throw new SQLException(
                    "Row has "
                            + row.getValuesCount()
                            + " values but only "
                            + columnCount
                            + " columns are declared");
        }
        rowSet.moveToInsertRow();
        for (int index = 0; index < columnCount; index++) {
            var value = index < row.getValuesCount() ? row.getValues(index) : null;
            var sqlType = serializedResultSet.getMetadata(index).getSqlType();
            rowSet.updateObject(index + 1, convert(value, sqlType, index + 1));
        }
        rowSet.insertRow();
    }

    private static RowSetMetaDataImpl metaData(SerializedResultSet serializedResultSet)
            throws SQLException {
        var metadata = new RowSetMetaDataImpl();
        metadata.setColumnCount(serializedResultSet.getMetadataCount());

        for (int index = 0; index < serializedResultSet.getMetadataCount(); index++) {
            var columnIndex = index + 1;
            var column = serializedResultSet.getMetadata(index);
            configureColumn(metadata, columnIndex, column);
        }

        return metadata;
    }

    private static void configureColumn(
            RowSetMetaDataImpl metadata, int columnIndex, ColumnMetadata column)
            throws SQLException {
        metadata.setAutoIncrement(
                columnIndex, column.hasAutoIncrement() && column.getAutoIncrement());
        metadata.setCaseSensitive(columnIndex, true);
        metadata.setSearchable(columnIndex, true);
        metadata.setCurrency(columnIndex, false);
        metadata.setNullable(
                columnIndex,
                column.hasNullable()
                        ? column.getNullable()
                        : ResultSetMetaDataDefaults.NULLABLE_UNKNOWN);
        metadata.setSigned(columnIndex, isSigned(column.getSqlType()));
        metadata.setColumnDisplaySize(
                columnIndex, Math.max(column.getLabel().length(), column.getName().length()));
        metadata.setColumnLabel(
                columnIndex, column.getLabel().isBlank() ? column.getName() : column.getLabel());
        metadata.setColumnName(
                columnIndex, column.getName().isBlank() ? column.getLabel() : column.getName());
        metadata.setSchemaName(columnIndex, column.hasSchemaName() ? column.getSchemaName() : "");
        metadata.setPrecision(columnIndex, column.hasPrecision() ? column.getPrecision() : 0);
        metadata.setScale(columnIndex, column.hasScale() ? column.getScale() : 0);
        metadata.setTableName(columnIndex, column.hasTableName() ? column.getTableName() : "");
        metadata.setCatalogName(
                columnIndex, column.hasCatalogName() ? column.getCatalogName() : "");
        metadata.setColumnType(columnIndex, column.getSqlType());
        metadata.setColumnTypeName(columnIndex, column.getTypeName());
    }

    private static boolean isSigned(int sqlType) {
        return SIGNED_SQL_TYPES.contains(sqlType);
    }

    private static Object convert(JdbcValue value, int sqlType, int columnIndex)
            throws SQLException {
        if (value == null || isNull(value)) {
            return null;
        }
        if (sqlType == Types.TIME_WITH_TIMEZONE || sqlType == Types.TIMESTAMP_WITH_TIMEZONE) {
            throw new SQLFeatureNotSupportedException(
                    "Column "
                            + columnIndex
                            + " uses SQL type "
                            + sqlType
                            + ", whose time zone offset is not transported by MockJDBC");
        }
        try {
            return convertBySqlType(value, sqlType);
        } catch (RuntimeException e) {
            throw new SQLDataException(
                    "Cannot convert value of column "
                            + columnIndex
                            + " to SQL type "
                            + sqlType
                            + ": "
                            + e.getMessage(),
                    e);
        }
    }

    private static Object convertBySqlType(JdbcValue value, int sqlType) throws SQLException {
        if (isIntegralType(sqlType)) return convertIntegralType(value, sqlType);
        if (isFloatingType(sqlType)) return convertFloatingType(value, sqlType);
        if (isDecimalType(sqlType)) return toBigDecimal(value);
        if (sqlType == Types.BOOLEAN || sqlType == Types.BIT) return toBoolean(value);
        if (isTemporalType(sqlType)) return convertTemporalType(value, sqlType);
        if (sqlType == Types.BLOB) return toBlob(value);
        if (sqlType == Types.CLOB) return toClob(value);
        return convertByKind(value);
    }

    private static boolean isIntegralType(int sqlType) {
        return sqlType == Types.TINYINT
                || sqlType == Types.SMALLINT
                || sqlType == Types.INTEGER
                || sqlType == Types.BIGINT;
    }

    private static boolean isFloatingType(int sqlType) {
        return sqlType == Types.REAL || sqlType == Types.FLOAT || sqlType == Types.DOUBLE;
    }

    private static boolean isDecimalType(int sqlType) {
        return sqlType == Types.NUMERIC || sqlType == Types.DECIMAL;
    }

    private static boolean isTemporalType(int sqlType) {
        return sqlType == Types.DATE || sqlType == Types.TIME || sqlType == Types.TIMESTAMP;
    }

    private static Object convertIntegralType(JdbcValue value, int sqlType) {
        return switch (sqlType) {
            case Types.TINYINT -> (int) inRange(exactLong(value), Byte.MIN_VALUE, Byte.MAX_VALUE);
            case Types.SMALLINT ->
                    (int) inRange(exactLong(value), Short.MIN_VALUE, Short.MAX_VALUE);
            case Types.INTEGER ->
                    (int) inRange(exactLong(value), Integer.MIN_VALUE, Integer.MAX_VALUE);
            default -> exactLong(value);
        };
    }

    private static Object convertFloatingType(JdbcValue value, int sqlType) {
        if (sqlType == Types.REAL) return (float) toDouble(value);
        return toDouble(value);
    }

    private static Object convertTemporalType(JdbcValue value, int sqlType) throws SQLException {
        return switch (sqlType) {
            case Types.DATE -> toDate(value);
            case Types.TIME -> toTime(value);
            default -> toTimestamp(value);
        };
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
            default -> convertComplexKind(value);
        };
    }

    private static Object convertComplexKind(JdbcValue value) {
        return switch (value.getKindCase()) {
            case BYTES_VAL -> value.getBytesVal().toByteArray();
            case DECIMAL_VAL -> new BigDecimal(value.getDecimalVal());
            case TIMESTAMP_VAL -> instantOf(value);
            default -> null;
        };
    }

    private static long exactLong(JdbcValue value) {
        return switch (value.getKindCase()) {
            case LONG_VAL -> value.getLongVal();
            case DOUBLE_VAL -> BigDecimal.valueOf(value.getDoubleVal()).longValueExact();
            case DECIMAL_VAL -> new BigDecimal(value.getDecimalVal()).longValueExact();
            case STRING_VAL -> new BigDecimal(value.getStringVal().trim()).longValueExact();
            case BOOL_VAL -> value.getBoolVal() ? 1L : 0L;
            default ->
                    throw new IllegalArgumentException(
                            "unsupported value kind " + value.getKindCase());
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
            default ->
                    throw new IllegalArgumentException(
                            "unsupported value kind " + value.getKindCase());
        };
    }

    private static BigDecimal toBigDecimal(JdbcValue value) {
        return switch (value.getKindCase()) {
            case DECIMAL_VAL -> new BigDecimal(value.getDecimalVal());
            case LONG_VAL -> BigDecimal.valueOf(value.getLongVal());
            case DOUBLE_VAL -> BigDecimal.valueOf(value.getDoubleVal());
            case STRING_VAL -> new BigDecimal(value.getStringVal().trim());
            default ->
                    throw new IllegalArgumentException(
                            "unsupported value kind " + value.getKindCase());
        };
    }

    private static Boolean toBoolean(JdbcValue value) {
        return switch (value.getKindCase()) {
            case BOOL_VAL -> value.getBoolVal();
            case LONG_VAL -> value.getLongVal() != 0L;
            case STRING_VAL -> parseBoolean(value.getStringVal());
            default ->
                    throw new IllegalArgumentException(
                            "unsupported value kind " + value.getKindCase());
        };
    }

    private static boolean parseBoolean(String text) {
        var normalized = text.trim().toLowerCase(Locale.ROOT);
        if (TRUE_VALUES.contains(normalized)) return true;
        if (FALSE_VALUES.contains(normalized)) return false;
        throw new IllegalArgumentException("not a boolean: " + text);
    }

    private static Date toDate(JdbcValue value) throws SQLDataException {
        return switch (value.getKindCase()) {
            case TIMESTAMP_VAL ->
                    toJdbcDate(instantOf(value).toInstant().atZone(ZoneOffset.UTC).toLocalDate());
            case STRING_VAL -> toJdbcDate(LocalDate.parse(datePart(value.getStringVal())));
            default ->
                    throw new IllegalArgumentException(
                            "unsupported value kind " + value.getKindCase());
        };
    }

    private static Date toJdbcDate(LocalDate date) throws SQLDataException {
        var jdbcDate = Date.valueOf(date);
        if (!jdbcDate.toLocalDate().equals(date)) {
            throw new SQLDataException(
                    "SQL DATE "
                            + date
                            + " cannot be represented in default time zone "
                            + ZoneId.systemDefault().getId());
        }
        return jdbcDate;
    }

    private static Time toTime(JdbcValue value) {
        return switch (value.getKindCase()) {
            case TIMESTAMP_VAL ->
                    Time.valueOf(
                            instantOf(value)
                                    .toInstant()
                                    .atZone(ZoneOffset.UTC)
                                    .toLocalTime()
                                    .withNano(0));
            case STRING_VAL ->
                    Time.valueOf(LocalTime.parse(timePart(value.getStringVal())).withNano(0));
            default ->
                    throw new IllegalArgumentException(
                            "unsupported value kind " + value.getKindCase());
        };
    }

    private static Timestamp toTimestamp(JdbcValue value) {
        return switch (value.getKindCase()) {
            case TIMESTAMP_VAL -> instantOf(value);
            case STRING_VAL -> parseTimestamp(value.getStringVal().trim());
            default ->
                    throw new IllegalArgumentException(
                            "unsupported value kind " + value.getKindCase());
        };
    }

    private static Blob toBlob(JdbcValue value) throws SQLException {
        if (value.getKindCase() != JdbcValue.KindCase.BYTES_VAL) {
            throw new IllegalArgumentException("expected bytes but got " + value.getKindCase());
        }
        return new SerialBlob(value.getBytesVal().toByteArray());
    }

    private static Clob toClob(JdbcValue value) throws SQLException {
        if (value.getKindCase() != JdbcValue.KindCase.STRING_VAL) {
            throw new IllegalArgumentException("expected string but got " + value.getKindCase());
        }
        return new SerialClob(value.getStringVal().toCharArray());
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
        return Timestamp.from(
                Instant.ofEpochSecond(
                        value.getTimestampVal().getSeconds(), value.getTimestampVal().getNanos()));
    }

    private static final class ResultSetMetaDataDefaults {
        private static final int NULLABLE_UNKNOWN = 2;

        private ResultSetMetaDataDefaults() {}
    }
}
