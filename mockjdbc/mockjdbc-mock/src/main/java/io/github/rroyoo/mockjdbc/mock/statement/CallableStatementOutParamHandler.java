package io.github.rroyoo.mockjdbc.mock.statement;

import io.github.rroyoo.mockjdbc.mock.JdbcValue;
import io.github.rroyoo.mockjdbc.mock.ParameterMetadata;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Date;
import java.sql.SQLException;
import java.sql.Time;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.Calendar;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks OUT parameter registrations for CallableStatement and exposes
 * typed getters by index and by name. Registered OUT parameters return null
 * (or the primitive default) until the mock server returns values for them in
 * {@code MockedQuery.callable_statement.parameters}; those values are matched by
 * 1-based parameter index, so name-based getters keep returning null/defaults.
 */
final class CallableStatementOutParamHandler {

    private static final int MODE_OUT = 4;
    private static final int MODE_IN_OUT = 2;

    // keyed by 1-based parameter index
    private final ConcurrentHashMap<Integer, OutParam> byIndex = new ConcurrentHashMap<>();
    // keyed by parameter name
    private final ConcurrentHashMap<String, OutParam> byName = new ConcurrentHashMap<>();

    // OUT/INOUT values returned by the mock server for the last execution, keyed by 1-based index
    private final ConcurrentHashMap<Integer, JdbcValue> valuesByIndex = new ConcurrentHashMap<>();

    private final StatementLifecycleHandler lifecycle;
    private volatile boolean lastWasNull = true;

    CallableStatementOutParamHandler(StatementLifecycleHandler lifecycle) {
        this.lifecycle = lifecycle;
    }

    /** Replaces the OUT values with those returned by the latest execution (IN-only entries are ignored). */
    void applyOutValues(List<ParameterMetadata> returned) {
        valuesByIndex.clear();
        for (var parameter : returned) {
            if ((parameter.getMode() == MODE_OUT || parameter.getMode() == MODE_IN_OUT) && parameter.hasValue()) {
                valuesByIndex.put(parameter.getIndex(), parameter.getValue());
            }
        }
    }

    // ---- registration -------------------------------------------------------

    public void registerOutParameter(int parameterIndex, int sqlType) throws SQLException {
        lifecycle.assertOpen();
        byIndex.put(parameterIndex, new OutParam(sqlType, ""));
    }

    public void registerOutParameter(int parameterIndex, int sqlType, int scale) throws SQLException {
        lifecycle.assertOpen();
        byIndex.put(parameterIndex, new OutParam(sqlType, ""));
    }

    public void registerOutParameter(int parameterIndex, int sqlType, String typeName) throws SQLException {
        lifecycle.assertOpen();
        byIndex.put(parameterIndex, new OutParam(sqlType, typeName != null ? typeName : ""));
    }

    public void registerOutParameter(String parameterName, int sqlType) throws SQLException {
        lifecycle.assertOpen();
        byName.put(parameterName, new OutParam(sqlType, ""));
    }

    public void registerOutParameter(String parameterName, int sqlType, int scale) throws SQLException {
        lifecycle.assertOpen();
        byName.put(parameterName, new OutParam(sqlType, ""));
    }

    public void registerOutParameter(String parameterName, int sqlType, String typeName) throws SQLException {
        lifecycle.assertOpen();
        byName.put(parameterName, new OutParam(sqlType, typeName != null ? typeName : ""));
    }

    // ---- wasNull ------------------------------------------------------------

    public boolean wasNull() throws SQLException {
        lifecycle.assertOpen();
        return lastWasNull;
    }

    // ---- getters by index ---------------------------------------------------

    public String getString(int parameterIndex) throws SQLException {
        var value = read(parameterIndex);
        return value == null ? null : asString(value);
    }

    public boolean getBoolean(int parameterIndex) throws SQLException {
        var value = read(parameterIndex);
        return value != null && asBoolean(value);
    }

    public byte getByte(int parameterIndex) throws SQLException {
        var value = read(parameterIndex);
        return value == null ? 0 : (byte) asLong(value);
    }

    public short getShort(int parameterIndex) throws SQLException {
        var value = read(parameterIndex);
        return value == null ? 0 : (short) asLong(value);
    }

    public int getInt(int parameterIndex) throws SQLException {
        var value = read(parameterIndex);
        return value == null ? 0 : (int) asLong(value);
    }

    public long getLong(int parameterIndex) throws SQLException {
        var value = read(parameterIndex);
        return value == null ? 0L : asLong(value);
    }

    public float getFloat(int parameterIndex) throws SQLException {
        var value = read(parameterIndex);
        return value == null ? 0f : (float) asDouble(value);
    }

    public double getDouble(int parameterIndex) throws SQLException {
        var value = read(parameterIndex);
        return value == null ? 0d : asDouble(value);
    }

    public byte[] getBytes(int parameterIndex) throws SQLException {
        var value = read(parameterIndex);
        return value == null ? null : asBytes(value);
    }

    public BigDecimal getBigDecimal(int parameterIndex) throws SQLException {
        var value = read(parameterIndex);
        return value == null ? null : asBigDecimal(value);
    }

    public Object getObject(int parameterIndex) throws SQLException {
        var value = read(parameterIndex);
        return value == null ? null : asObject(value, byIndex.get(parameterIndex).sqlType());
    }

    public Date getDate(int parameterIndex) throws SQLException {
        var value = read(parameterIndex);
        return value == null ? null : new Date(asTimestamp(value).getTime());
    }

    public Time getTime(int parameterIndex) throws SQLException {
        var value = read(parameterIndex);
        return value == null ? null : new Time(asTimestamp(value).getTime());
    }

    public Timestamp getTimestamp(int parameterIndex) throws SQLException {
        var value = read(parameterIndex);
        return value == null ? null : asTimestamp(value);
    }

    public Date getDate(int parameterIndex, Calendar cal) throws SQLException {
        return getDate(parameterIndex);
    }

    public Time getTime(int parameterIndex, Calendar cal) throws SQLException {
        return getTime(parameterIndex);
    }

    public Timestamp getTimestamp(int parameterIndex, Calendar cal) throws SQLException {
        return getTimestamp(parameterIndex);
    }

    public <T> T getObject(int parameterIndex, Class<T> type) throws SQLException {
        var value = read(parameterIndex);
        return value == null ? null : convert(value, byIndex.get(parameterIndex).sqlType(), type);
    }

    // ---- getters by name ----------------------------------------------------

    public String getString(String parameterName) throws SQLException {
        assertRegistered(parameterName);
        return null;
    }

    public boolean getBoolean(String parameterName) throws SQLException {
        assertRegistered(parameterName);
        return false;
    }

    public byte getByte(String parameterName) throws SQLException {
        assertRegistered(parameterName);
        return 0;
    }

    public short getShort(String parameterName) throws SQLException {
        assertRegistered(parameterName);
        return 0;
    }

    public int getInt(String parameterName) throws SQLException {
        assertRegistered(parameterName);
        return 0;
    }

    public long getLong(String parameterName) throws SQLException {
        assertRegistered(parameterName);
        return 0L;
    }

    public float getFloat(String parameterName) throws SQLException {
        assertRegistered(parameterName);
        return 0f;
    }

    public double getDouble(String parameterName) throws SQLException {
        assertRegistered(parameterName);
        return 0d;
    }

    public byte[] getBytes(String parameterName) throws SQLException {
        assertRegistered(parameterName);
        return null;
    }

    public BigDecimal getBigDecimal(String parameterName) throws SQLException {
        assertRegistered(parameterName);
        return null;
    }

    public Object getObject(String parameterName) throws SQLException {
        assertRegistered(parameterName);
        return null;
    }

    public Date getDate(String parameterName) throws SQLException {
        assertRegistered(parameterName);
        return null;
    }

    public Time getTime(String parameterName) throws SQLException {
        assertRegistered(parameterName);
        return null;
    }

    public Timestamp getTimestamp(String parameterName) throws SQLException {
        assertRegistered(parameterName);
        return null;
    }

    public Date getDate(String parameterName, Calendar cal) throws SQLException {
        return getDate(parameterName);
    }

    public Time getTime(String parameterName, Calendar cal) throws SQLException {
        return getTime(parameterName);
    }

    public Timestamp getTimestamp(String parameterName, Calendar cal) throws SQLException {
        return getTimestamp(parameterName);
    }

    public <T> T getObject(String parameterName, Class<T> type) throws SQLException {
        assertRegistered(parameterName);
        return null;
    }

    // ---- helpers ------------------------------------------------------------

    /** Returns the server-provided value, or null (recording wasNull) when absent or SQL NULL. */
    private JdbcValue read(int index) throws SQLException {
        assertRegistered(index);
        var value = valuesByIndex.get(index);
        lastWasNull = value == null || value.getKindCase() == JdbcValue.KindCase.IS_NULL
                || value.getKindCase() == JdbcValue.KindCase.KIND_NOT_SET;
        return lastWasNull ? null : value;
    }

    private static String asString(JdbcValue value) {
        return switch (value.getKindCase()) {
            case STRING_VAL -> value.getStringVal();
            case LONG_VAL -> String.valueOf(value.getLongVal());
            case DOUBLE_VAL -> String.valueOf(value.getDoubleVal());
            case BOOL_VAL -> String.valueOf(value.getBoolVal());
            case DECIMAL_VAL -> value.getDecimalVal();
            case BYTES_VAL -> new String(value.getBytesVal().toByteArray(), StandardCharsets.UTF_8);
            case TIMESTAMP_VAL -> toTimestamp(value).toString();
            default -> null;
        };
    }

    private static long asLong(JdbcValue value) throws SQLException {
        try {
            return switch (value.getKindCase()) {
                case LONG_VAL -> value.getLongVal();
                case DOUBLE_VAL -> (long) value.getDoubleVal();
                case BOOL_VAL -> value.getBoolVal() ? 1L : 0L;
                case DECIMAL_VAL -> new BigDecimal(value.getDecimalVal()).longValue();
                case STRING_VAL -> new BigDecimal(value.getStringVal().trim()).longValue();
                default -> throw new SQLException("Cannot convert OUT parameter of kind " + value.getKindCase() + " to a number");
            };
        } catch (NumberFormatException e) {
            throw new SQLException("Cannot convert OUT parameter value to a number", e);
        }
    }

    private static double asDouble(JdbcValue value) throws SQLException {
        return value.getKindCase() == JdbcValue.KindCase.DOUBLE_VAL ? value.getDoubleVal() : asBigDecimal(value).doubleValue();
    }

    private static boolean asBoolean(JdbcValue value) throws SQLException {
        return switch (value.getKindCase()) {
            case BOOL_VAL -> value.getBoolVal();
            case STRING_VAL -> "true".equalsIgnoreCase(value.getStringVal()) || "1".equals(value.getStringVal());
            default -> asLong(value) != 0L;
        };
    }

    private static BigDecimal asBigDecimal(JdbcValue value) throws SQLException {
        try {
            return switch (value.getKindCase()) {
                case DECIMAL_VAL -> new BigDecimal(value.getDecimalVal());
                case LONG_VAL -> BigDecimal.valueOf(value.getLongVal());
                case DOUBLE_VAL -> BigDecimal.valueOf(value.getDoubleVal());
                case STRING_VAL -> new BigDecimal(value.getStringVal().trim());
                default -> throw new SQLException("Cannot convert OUT parameter of kind " + value.getKindCase() + " to a decimal");
            };
        } catch (NumberFormatException e) {
            throw new SQLException("Cannot convert OUT parameter value to a decimal", e);
        }
    }

    private static byte[] asBytes(JdbcValue value) {
        return value.getKindCase() == JdbcValue.KindCase.BYTES_VAL
                ? value.getBytesVal().toByteArray()
                : asString(value).getBytes(StandardCharsets.UTF_8);
    }

    private static Timestamp toTimestamp(JdbcValue value) {
        return Timestamp.from(Instant.ofEpochSecond(value.getTimestampVal().getSeconds(), value.getTimestampVal().getNanos()));
    }

    private static Timestamp asTimestamp(JdbcValue value) throws SQLException {
        if (value.getKindCase() == JdbcValue.KindCase.TIMESTAMP_VAL) {
            return toTimestamp(value);
        }
        if (value.getKindCase() == JdbcValue.KindCase.STRING_VAL) {
            var text = value.getStringVal().trim();
            try {
                return Timestamp.valueOf(text.length() <= 10 ? text + " 00:00:00" : text);
            } catch (IllegalArgumentException e) {
                throw new SQLException("Cannot convert OUT parameter value to a timestamp: " + text, e);
            }
        }
        throw new SQLException("Cannot convert OUT parameter of kind " + value.getKindCase() + " to a timestamp");
    }

    private static Object asObject(JdbcValue value, int sqlType) throws SQLException {
        return switch (value.getKindCase()) {
            case STRING_VAL -> value.getStringVal();
            case LONG_VAL -> switch (sqlType) {
                case Types.INTEGER, Types.SMALLINT, Types.TINYINT -> (Object) (int) value.getLongVal();
                default -> (Object) value.getLongVal();
            };
            case DOUBLE_VAL -> value.getDoubleVal();
            case BOOL_VAL -> value.getBoolVal();
            case DECIMAL_VAL -> new BigDecimal(value.getDecimalVal());
            case BYTES_VAL -> value.getBytesVal().toByteArray();
            case TIMESTAMP_VAL -> toTimestamp(value);
            default -> null;
        };
    }

    private static <T> T convert(JdbcValue value, int sqlType, Class<T> type) throws SQLException {
        Object converted;
        if (type == String.class) converted = asString(value);
        else if (type == Integer.class) converted = (int) asLong(value);
        else if (type == Long.class) converted = asLong(value);
        else if (type == Short.class) converted = (short) asLong(value);
        else if (type == Double.class) converted = asDouble(value);
        else if (type == Boolean.class) converted = asBoolean(value);
        else if (type == BigDecimal.class) converted = asBigDecimal(value);
        else if (type == Timestamp.class) converted = asTimestamp(value);
        else if (type == byte[].class) converted = asBytes(value);
        else {
            converted = asObject(value, sqlType);
            if (!type.isInstance(converted)) {
                throw new SQLException("Cannot convert OUT parameter to " + type.getName());
            }
        }
        return type.cast(converted);
    }

    private void assertRegistered(int index) throws SQLException {
        lifecycle.assertOpen();
        if (!byIndex.containsKey(index)) {
            throw new SQLException("OUT parameter at index " + index + " was not registered.");
        }
    }

    private void assertRegistered(String name) throws SQLException {
        lifecycle.assertOpen();
        if (!byName.containsKey(name)) {
            throw new SQLException("OUT parameter '" + name + "' was not registered.");
        }
    }

    private record OutParam(int sqlType, String typeName) {}
}
