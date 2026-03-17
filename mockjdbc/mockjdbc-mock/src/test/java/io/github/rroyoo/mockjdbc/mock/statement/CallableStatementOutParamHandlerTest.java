package io.github.rroyoo.mockjdbc.mock.statement;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.sql.Types;
import java.util.Calendar;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CallableStatementOutParamHandlerTest {

    @Test
    @DisplayName("Given an OUT parameter registered by index, when typed getters are called, then default values are returned")
    void shouldReturnDefaultValuesForRegisteredIndexOutParameter() throws Exception {
        var handler = new CallableStatementOutParamHandler(new StatementLifecycleHandler());
        handler.registerOutParameter(1, Types.INTEGER);

        assertNull(handler.getString(1));
        assertFalse(handler.getBoolean(1));
        assertEquals(0, handler.getInt(1));
        assertEquals(0L, handler.getLong(1));
        assertEquals(0d, handler.getDouble(1), 0.0001d);
        assertNull(handler.getBytes(1));
        assertNull(handler.getObject(1));
        assertNull(handler.getDate(1, Calendar.getInstance()));
        assertNull(handler.getTimestamp(1, Calendar.getInstance()));
        assertTrue(handler.wasNull());
    }

    @Test
    @DisplayName("Given an OUT parameter registered by name, when typed getters are called, then default values are returned")
    void shouldReturnDefaultValuesForRegisteredNamedOutParameter() throws Exception {
        var handler = new CallableStatementOutParamHandler(new StatementLifecycleHandler());
        handler.registerOutParameter("out_status", Types.VARCHAR, "VARCHAR");

        assertNull(handler.getString("out_status"));
        assertEquals(0, handler.getInt("out_status"));
        assertEquals(0f, handler.getFloat("out_status"), 0.0001f);
        assertNull(handler.getBigDecimal("out_status"));
        assertNull(handler.getObject("out_status", String.class));
        assertTrue(handler.wasNull());
    }

    @Test
    @DisplayName("Given an unregistered OUT parameter, when getter is called, then it throws SQLException")
    void shouldThrowForUnregisteredOutParameter() {
        var handler = new CallableStatementOutParamHandler(new StatementLifecycleHandler());

        assertThrows(SQLException.class, () -> handler.getInt(1));
        assertThrows(SQLException.class, () -> handler.getString("missing"));
    }

    @Test
    @DisplayName("Given a closed lifecycle, when registering or reading OUT parameters, then it throws SQLException")
    void shouldFailWhenStatementIsClosed() throws Exception {
        var lifecycle = new StatementLifecycleHandler();
        lifecycle.close();
        var handler = new CallableStatementOutParamHandler(lifecycle);

        assertThrows(SQLException.class, () -> handler.registerOutParameter(1, Types.INTEGER));
        assertThrows(SQLException.class, handler::wasNull);
    }
}

