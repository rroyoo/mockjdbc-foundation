package io.github.rroyoo.mockjdbc.mock.statement;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.ResultSet;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StatementConfigHandlerTest {

    @Test
    @DisplayName("Given a new statement config handler, when reading defaults, then JDBC-like defaults are returned")
    void shouldReturnJdbcDefaults() throws Exception {
        var handler = new StatementConfigHandler();

        assertEquals(0, handler.getMaxRows());
        assertEquals(0L, handler.getLargeMaxRows());
        assertEquals(0, handler.getQueryTimeout());
        assertEquals(0, handler.getFetchSize());
        assertEquals(ResultSet.FETCH_FORWARD, handler.getFetchDirection());
        assertEquals(ResultSet.TYPE_FORWARD_ONLY, handler.getResultSetType());
        assertEquals(ResultSet.CONCUR_READ_ONLY, handler.getResultSetConcurrency());
        assertEquals(ResultSet.CLOSE_CURSORS_AT_COMMIT, handler.getResultSetHoldability());
    }

    @Test
    @DisplayName("Given a statement config handler, when values are set, then getters return the configured values")
    void shouldStoreConfiguredValues() throws Exception {
        var handler = new StatementConfigHandler();

        handler.setMaxRows(10);
        handler.setLargeMaxRows(100L);
        handler.setQueryTimeout(5);
        handler.setFetchSize(50);
        handler.setFetchDirection(ResultSet.FETCH_REVERSE);

        assertEquals(10, handler.getMaxRows());
        assertEquals(100L, handler.getLargeMaxRows());
        assertEquals(5, handler.getQueryTimeout());
        assertEquals(50, handler.getFetchSize());
        assertEquals(ResultSet.FETCH_REVERSE, handler.getFetchDirection());
    }
}

