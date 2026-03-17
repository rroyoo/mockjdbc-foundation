package io.github.rroyoo.mockjdbc.mock.connection;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LifecycleHandlerTest {

    @Test
    @DisplayName("Given a new handler, when isClosed is called, then it returns false")
    void shouldReturnNotClosedByDefault() throws Exception {
        var handler = new LifecycleHandler();

        assertFalse(handler.isClosed());
    }

    @Test
    @DisplayName("Given an open handler, when close is called, then isClosed returns true")
    void shouldReturnClosedAfterCloseIsCalled() throws Exception {
        var handler = new LifecycleHandler();

        handler.close();

        assertTrue(handler.isClosed());
    }
}

