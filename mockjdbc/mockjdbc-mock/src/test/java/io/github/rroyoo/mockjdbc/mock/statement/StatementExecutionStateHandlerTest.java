package io.github.rroyoo.mockjdbc.mock.statement;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StatementExecutionStateHandlerTest {

    @Test
    @DisplayName("Given a new execution state handler, when reading defaults, then no current result set and -1 counts are returned")
    void shouldReturnDefaultExecutionState() throws Exception {
        var handler = new StatementExecutionStateHandler();

        assertNull(handler.getResultSet());
        assertEquals(-1, handler.getUpdateCount());
        assertEquals(-1L, handler.getLargeUpdateCount());
        assertFalse(handler.isCloseOnCompletion());
    }

    @Test
    @DisplayName("Given a handler, when storing a result set, then it is exposed and update counts are reset")
    void shouldStoreResultSetAndResetCounts() throws Exception {
        var handler = new StatementExecutionStateHandler();
        var resultSet = trackingResultSet(new AtomicBoolean(false));

        handler.storeUpdateCount(7);
        handler.storeResultSet(resultSet);

        assertSame(resultSet, handler.getResultSet());
        assertEquals(-1, handler.getUpdateCount());
        assertEquals(-1L, handler.getLargeUpdateCount());
    }

    @Test
    @DisplayName("Given an existing result set, when storing update count, then current result set is closed and count is stored")
    void shouldCloseCurrentResultSetWhenStoringUpdateCount() throws Exception {
        var handler = new StatementExecutionStateHandler();
        var closed = new AtomicBoolean(false);

        handler.storeResultSet(trackingResultSet(closed));
        handler.storeUpdateCount(3);

        assertTrue(closed.get());
        assertNull(handler.getResultSet());
        assertEquals(3, handler.getUpdateCount());
        assertEquals(3L, handler.getLargeUpdateCount());
    }

    @Test
    @DisplayName("Given a large update count bigger than int max, when storing it, then int count is capped to Integer.MAX_VALUE")
    void shouldCapUpdateCountWhenStoringLargeUpdateCount() throws Exception {
        var handler = new StatementExecutionStateHandler();

        handler.storeLargeUpdateCount((long) Integer.MAX_VALUE + 10L);

        assertEquals(Integer.MAX_VALUE, handler.getUpdateCount());
        assertEquals((long) Integer.MAX_VALUE + 10L, handler.getLargeUpdateCount());
    }

    @Test
    @DisplayName("Given a current result set, when getMoreResults KEEP_CURRENT_RESULT is called, then result set is kept")
    void shouldKeepCurrentResultSetWhenKeepCurrentFlagIsUsed() throws Exception {
        var handler = new StatementExecutionStateHandler();
        var resultSet = trackingResultSet(new AtomicBoolean(false));
        handler.storeResultSet(resultSet);

        assertFalse(handler.getMoreResults(Statement.KEEP_CURRENT_RESULT));
        assertSame(resultSet, handler.getResultSet());
    }

    @Test
    @DisplayName("Given a current result set, when getMoreResults CLOSE_CURRENT_RESULT is called, then it closes and clears current result set")
    void shouldCloseCurrentResultSetWhenCloseCurrentFlagIsUsed() throws Exception {
        var handler = new StatementExecutionStateHandler();
        var closed = new AtomicBoolean(false);
        handler.storeResultSet(trackingResultSet(closed));

        assertFalse(handler.getMoreResults(Statement.CLOSE_CURRENT_RESULT));
        assertTrue(closed.get());
        assertNull(handler.getResultSet());
    }

    @Test
    @DisplayName("Given an invalid getMoreResults flag, when getMoreResults is called, then it throws SQLException")
    void shouldThrowForInvalidGetMoreResultsFlag() {
        var handler = new StatementExecutionStateHandler();

        assertThrows(SQLException.class, () -> handler.getMoreResults(999));
    }

    @Test
    @DisplayName("Given a new handler, when closeOnCompletion is enabled, then isCloseOnCompletion returns true")
    void shouldEnableCloseOnCompletionFlag() throws Exception {
        var handler = new StatementExecutionStateHandler();

        handler.closeOnCompletion();

        assertTrue(handler.isCloseOnCompletion());
    }

    private static ResultSet trackingResultSet(AtomicBoolean closed) {
        return (ResultSet) Proxy.newProxyInstance(
                StatementExecutionStateHandlerTest.class.getClassLoader(),
                new Class<?>[]{ResultSet.class},
                (proxy, method, args) -> {
                    if ("close".equals(method.getName())) {
                        closed.set(true);
                        return null;
                    }
                    if ("isClosed".equals(method.getName())) {
                        return closed.get();
                    }
                    throw new UnsupportedOperationException("Method not implemented in test proxy: " + method.getName());
                }
        );
    }
}

