package io.github.rroyoo.mockjdbc.mock.statement;

import io.github.rroyoo.mockjdbc.mock.JdbcValue;
import io.github.rroyoo.mockjdbc.mock.ParameterMetadata;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.sql.Types;
import java.util.Calendar;
import java.util.List;

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

    @Test
    @DisplayName("Given OUT values applied from the server, when typed getters are called, then values are converted and wasNull is false")
    void shouldConvertAppliedOutValues() throws Exception {
        var handler = new CallableStatementOutParamHandler(new StatementLifecycleHandler());
        handler.registerOutParameter(1, Types.DECIMAL);
        handler.registerOutParameter(2, Types.BOOLEAN);
        handler.registerOutParameter(3, Types.VARCHAR);
        handler.applyOutValues(List.of(
                out(1, JdbcValue.newBuilder().setDecimalVal("12.50").build()),
                out(2, JdbcValue.newBuilder().setBoolVal(true).build()),
                out(3, JdbcValue.newBuilder().setStringVal("2026-03-13 10:15:30").build())));

        assertEquals(new BigDecimal("12.50"), handler.getBigDecimal(1));
        assertEquals(12, handler.getInt(1));
        assertFalse(handler.wasNull());
        assertTrue(handler.getBoolean(2));
        assertEquals("2026-03-13 10:15:30", handler.getString(3));
        assertEquals(java.sql.Timestamp.valueOf("2026-03-13 10:15:30"), handler.getTimestamp(3));
    }

    @Test
    @DisplayName("Given applied values containing only IN parameters, when an OUT getter is called, then it returns defaults")
    void shouldIgnoreInParametersWhenApplyingOutValues() throws Exception {
        var handler = new CallableStatementOutParamHandler(new StatementLifecycleHandler());
        handler.registerOutParameter(1, Types.INTEGER);
        handler.applyOutValues(List.of(ParameterMetadata.newBuilder()
                .setIndex(1)
                .setMode(java.sql.ParameterMetaData.parameterModeIn)
                .setValue(JdbcValue.newBuilder().setLongVal(5).build())
                .build()));

        assertEquals(0, handler.getInt(1));
        assertTrue(handler.wasNull());
    }

    private static ParameterMetadata out(int index, JdbcValue value) {
        return ParameterMetadata.newBuilder()
                .setIndex(index)
                .setMode(java.sql.ParameterMetaData.parameterModeOut)
                .setValue(value)
                .build();
    }
}
