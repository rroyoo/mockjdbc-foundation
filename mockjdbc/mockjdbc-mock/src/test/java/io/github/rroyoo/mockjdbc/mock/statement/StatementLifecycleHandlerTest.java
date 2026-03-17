package io.github.rroyoo.mockjdbc.mock.statement;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StatementLifecycleHandlerTest {

    @Test
    @DisplayName("Given a new statement lifecycle handler, when isClosed is called, then it returns false")
    void shouldReturnNotClosedByDefault() throws Exception {
        var handler = new StatementLifecycleHandler();

        assertFalse(handler.isClosed());
    }

    @Test
    @DisplayName("Given an open statement lifecycle handler, when close is called, then isClosed returns true")
    void shouldReturnClosedAfterClose() throws Exception {
        var handler = new StatementLifecycleHandler();

        handler.close();

        assertTrue(handler.isClosed());
    }

    @Test
    @DisplayName("Given an open statement lifecycle handler, when assertOpen is called, then it does not throw")
    void shouldNotThrowWhenStatementIsOpen() {
        var handler = new StatementLifecycleHandler();

        assertDoesNotThrow(handler::assertOpen);
    }

    @Test
    @DisplayName("Given a closed statement lifecycle handler, when assertOpen is called, then it throws SQLException")
    void shouldThrowWhenStatementIsClosed() throws Exception {
        var handler = new StatementLifecycleHandler();
        handler.close();

        assertThrows(SQLException.class, handler::assertOpen);
    }
}

