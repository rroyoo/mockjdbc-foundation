package io.github.rroyoo.mockjdbc.mock.connection;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.SQLException;
import java.sql.Time;
import java.sql.Timestamp;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

@DisplayName("MockStatement parameter storage and binding tests")
class MockStatementParameterTest {

    private MockConnection mockConnection;
    private MockStatement statement;

    @BeforeEach
    void setUp() throws SQLException {
        mockConnection = mock(MockConnection.class);
        statement = new MockStatement(mockConnection, "SELECT * FROM users WHERE id = ?");
    }

    @Test
    @DisplayName("Should store string parameter at index 1")
    void shouldStoreStringParameter() throws SQLException {
        statement.setString(1, "test-value");

        Map<Integer, Object> params = statement.getParameters();
        assertEquals("test-value", params.get(1));
    }

    @Test
    @DisplayName("Should store integer parameter at index 1")
    void shouldStoreIntParameter() throws SQLException {
        statement.setInt(1, 42);

        Map<Integer, Object> params = statement.getParameters();
        assertEquals(42, params.get(1));
    }

    @Test
    @DisplayName("Should store long parameter at index 1")
    void shouldStoreLongParameter() throws SQLException {
        statement.setLong(1, 123456789L);

        Map<Integer, Object> params = statement.getParameters();
        assertEquals(123456789L, params.get(1));
    }

    @Test
    @DisplayName("Should store boolean parameter at index 1")
    void shouldStoreBooleanParameter() throws SQLException {
        statement.setBoolean(1, true);

        Map<Integer, Object> params = statement.getParameters();
        assertEquals(true, params.get(1));
    }

    @Test
    @DisplayName("Should store double parameter at index 1")
    void shouldStoreDoubleParameter() throws SQLException {
        statement.setDouble(1, 3.14159);

        Map<Integer, Object> params = statement.getParameters();
        assertEquals(3.14159, params.get(1));
    }

    @Test
    @DisplayName("Should store BigDecimal parameter at index 1")
    void shouldStoreBigDecimalParameter() throws SQLException {
        BigDecimal value = new BigDecimal("123.456");
        statement.setBigDecimal(1, value);

        Map<Integer, Object> params = statement.getParameters();
        assertEquals(value, params.get(1));
    }

    @Test
    @DisplayName("Should store Date parameter at index 1")
    void shouldStoreDateParameter() throws SQLException {
        Date date = new Date(System.currentTimeMillis());
        statement.setDate(1, date);

        Map<Integer, Object> params = statement.getParameters();
        assertEquals(date, params.get(1));
    }

    @Test
    @DisplayName("Should store Time parameter at index 1")
    void shouldStoreTimeParameter() throws SQLException {
        Time time = new Time(System.currentTimeMillis());
        statement.setTime(1, time);

        Map<Integer, Object> params = statement.getParameters();
        assertEquals(time, params.get(1));
    }

    @Test
    @DisplayName("Should store Timestamp parameter at index 1")
    void shouldStoreTimestampParameter() throws SQLException {
        Timestamp timestamp = new Timestamp(System.currentTimeMillis());
        statement.setTimestamp(1, timestamp);

        Map<Integer, Object> params = statement.getParameters();
        assertEquals(timestamp, params.get(1));
    }

    @Test
    @DisplayName("Should store null parameter at index 1")
    void shouldStoreNullParameter() throws SQLException {
        statement.setNull(1, java.sql.Types.VARCHAR);

        Map<Integer, Object> params = statement.getParameters();
        assertTrue(params.containsKey(1));
        assertNull(params.get(1));
    }

    @Test
    @DisplayName("Should store byte array parameter at index 1")
    void shouldStoreByteArrayParameter() throws SQLException {
        byte[] bytes = new byte[]{1, 2, 3, 4, 5};
        statement.setBytes(1, bytes);

        Map<Integer, Object> params = statement.getParameters();
        assertArrayEquals(bytes, (byte[]) params.get(1));
    }

    @Test
    @DisplayName("Should store multiple parameters at different indices")
    void shouldStoreMultipleParameters() throws SQLException {
        statement.setString(1, "first");
        statement.setInt(2, 42);
        statement.setBoolean(3, true);

        Map<Integer, Object> params = statement.getParameters();
        assertEquals(3, params.size());
        assertEquals("first", params.get(1));
        assertEquals(42, params.get(2));
        assertEquals(true, params.get(3));
    }

    @Test
    @DisplayName("Should overwrite parameter at same index")
    void shouldOverwriteParameterAtSameIndex() throws SQLException {
        statement.setString(1, "original");
        statement.setString(1, "updated");

        Map<Integer, Object> params = statement.getParameters();
        assertEquals("updated", params.get(1));
    }

    @Test
    @DisplayName("Should clear all parameters")
    void shouldClearAllParameters() throws SQLException {
        statement.setString(1, "test");
        statement.setInt(2, 42);
        statement.setBoolean(3, true);

        statement.clearParameters();

        Map<Integer, Object> params = statement.getParameters();
        assertTrue(params.isEmpty());
    }

    @ParameterizedTest
    @DisplayName("Should throw SQLException for invalid parameter index")
    @ValueSource(ints = {0, -1, -10, -100})
    void shouldThrowExceptionForInvalidParameterIndex(int invalidIndex) {
        SQLException exception = assertThrows(SQLException.class,
            () -> statement.setString(invalidIndex, "test"));

        assertTrue(exception.getMessage().contains("Parameter index must be >= 1"));
        assertTrue(exception.getMessage().contains(String.valueOf(invalidIndex)));
    }

    @Test
    @DisplayName("Should validate parameter index for setInt")
    void shouldValidateParameterIndexForSetInt() {
        SQLException exception = assertThrows(SQLException.class,
            () -> statement.setInt(0, 42));

        assertTrue(exception.getMessage().contains("Parameter index must be >= 1"));
    }

    @Test
    @DisplayName("Should validate parameter index for setNull")
    void shouldValidateParameterIndexForSetNull() {
        SQLException exception = assertThrows(SQLException.class,
            () -> statement.setNull(-1, java.sql.Types.VARCHAR));

        assertTrue(exception.getMessage().contains("Parameter index must be >= 1"));
    }

    @Test
    @DisplayName("Should return defensive copy of parameters map")
    void shouldReturnDefensiveCopyOfParameters() throws SQLException {
        statement.setString(1, "test");

        Map<Integer, Object> params1 = statement.getParameters();
        Map<Integer, Object> params2 = statement.getParameters();

        assertNotSame(params1, params2, "Should return different map instances");
        assertEquals(params1, params2, "But with same content");

        // Modify returned map should not affect statement's internal state
        params1.clear();
        assertEquals(1, statement.getParameters().size(), "Statement should still have 1 parameter");
    }

    @Test
    @DisplayName("Should handle sparse parameter indices")
    void shouldHandleSparseParameterIndices() throws SQLException {
        statement.setString(1, "first");
        statement.setString(5, "fifth");
        statement.setString(10, "tenth");

        Map<Integer, Object> params = statement.getParameters();
        assertEquals(3, params.size());
        assertEquals("first", params.get(1));
        assertEquals("fifth", params.get(5));
        assertEquals("tenth", params.get(10));
        assertFalse(params.containsKey(2));
        assertFalse(params.containsKey(3));
    }

    @Test
    @DisplayName("Should store Object parameter using setObject")
    void shouldStoreObjectParameter() throws SQLException {
        Object obj = "test-object";
        statement.setObject(1, obj);

        Map<Integer, Object> params = statement.getParameters();
        assertEquals(obj, params.get(1));
    }

    @Test
    @DisplayName("Should store Object parameter with target SQL type")
    void shouldStoreObjectParameterWithTargetType() throws SQLException {
        statement.setObject(1, "test", java.sql.Types.VARCHAR);

        Map<Integer, Object> params = statement.getParameters();
        assertEquals("test", params.get(1));
    }

    @Test
    @DisplayName("Should store float parameter")
    void shouldStoreFloatParameter() throws SQLException {
        statement.setFloat(1, 3.14f);

        Map<Integer, Object> params = statement.getParameters();
        assertEquals(3.14f, params.get(1));
    }

    @Test
    @DisplayName("Should store byte parameter")
    void shouldStoreByteParameter() throws SQLException {
        statement.setByte(1, (byte) 127);

        Map<Integer, Object> params = statement.getParameters();
        assertEquals((byte) 127, params.get(1));
    }

    @Test
    @DisplayName("Should store short parameter")
    void shouldStoreShortParameter() throws SQLException {
        statement.setShort(1, (short) 12345);

        Map<Integer, Object> params = statement.getParameters();
        assertEquals((short) 12345, params.get(1));
    }
}

