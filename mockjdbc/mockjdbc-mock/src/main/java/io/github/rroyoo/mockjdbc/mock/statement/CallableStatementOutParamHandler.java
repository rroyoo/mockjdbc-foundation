package io.github.rroyoo.mockjdbc.mock.statement;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.SQLException;
import java.sql.Time;
import java.sql.Timestamp;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks OUT parameter registrations for CallableStatement and exposes
 * typed getters by index and by name. All registered OUT params return null
 * until the mock server populates them — consistent with the no-op execution
 * model used in the rest of the mock.
 */
final class CallableStatementOutParamHandler {

    private static final int MODE_OUT = 4;
    private static final int MODE_IN_OUT = 2;

    // keyed by 1-based parameter index
    private final ConcurrentHashMap<Integer, OutParam> byIndex = new ConcurrentHashMap<>();
    // keyed by parameter name
    private final ConcurrentHashMap<String, OutParam> byName = new ConcurrentHashMap<>();

    private final StatementLifecycleHandler lifecycle;

    CallableStatementOutParamHandler(StatementLifecycleHandler lifecycle) {
        this.lifecycle = lifecycle;
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
        // Mock OUT params always return null values, so wasNull() is always true
        // after any getter call.
        return true;
    }

    // ---- getters by index ---------------------------------------------------

    public String getString(int parameterIndex) throws SQLException {
        assertRegistered(parameterIndex);
        return null;
    }

    public boolean getBoolean(int parameterIndex) throws SQLException {
        assertRegistered(parameterIndex);
        return false;
    }

    public byte getByte(int parameterIndex) throws SQLException {
        assertRegistered(parameterIndex);
        return 0;
    }

    public short getShort(int parameterIndex) throws SQLException {
        assertRegistered(parameterIndex);
        return 0;
    }

    public int getInt(int parameterIndex) throws SQLException {
        assertRegistered(parameterIndex);
        return 0;
    }

    public long getLong(int parameterIndex) throws SQLException {
        assertRegistered(parameterIndex);
        return 0L;
    }

    public float getFloat(int parameterIndex) throws SQLException {
        assertRegistered(parameterIndex);
        return 0f;
    }

    public double getDouble(int parameterIndex) throws SQLException {
        assertRegistered(parameterIndex);
        return 0d;
    }

    public byte[] getBytes(int parameterIndex) throws SQLException {
        assertRegistered(parameterIndex);
        return null;
    }

    public BigDecimal getBigDecimal(int parameterIndex) throws SQLException {
        assertRegistered(parameterIndex);
        return null;
    }

    public Object getObject(int parameterIndex) throws SQLException {
        assertRegistered(parameterIndex);
        return null;
    }

    public Date getDate(int parameterIndex) throws SQLException {
        assertRegistered(parameterIndex);
        return null;
    }

    public Time getTime(int parameterIndex) throws SQLException {
        assertRegistered(parameterIndex);
        return null;
    }

    public Timestamp getTimestamp(int parameterIndex) throws SQLException {
        assertRegistered(parameterIndex);
        return null;
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

    // ---- helpers ------------------------------------------------------------

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

