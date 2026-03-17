package io.github.rroyoo.mockjdbc.mock.connection;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TransactionHandlerTest {

    @Test
    @DisplayName("Given a new handler, when getAutoCommit is called, then it returns true by default")
    void shouldReturnAutoCommitTrueByDefault() throws Exception {
        var handler = new TransactionHandler();

        assertTrue(handler.getAutoCommit());
    }

    @Test
    @DisplayName("Given a handler, when setAutoCommit is called with false, then getAutoCommit returns false")
    void shouldReturnFalseAfterAutoCommitIsDisabled() throws Exception {
        var handler = new TransactionHandler();

        handler.setAutoCommit(false);

        assertFalse(handler.getAutoCommit());
    }

    @Test
    @DisplayName("Given a handler with autoCommit false, when setAutoCommit is called with true, then getAutoCommit returns true")
    void shouldReturnTrueAfterAutoCommitIsEnabledAgain() throws Exception {
        var handler = new TransactionHandler();
        handler.setAutoCommit(false);

        handler.setAutoCommit(true);

        assertTrue(handler.getAutoCommit());
    }
}

