package io.github.rroyoo.mockjdbc.mock.statement;

import io.github.rroyoo.mockjdbc.mock.driver.MockConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StatementMiscHandlerTest {

    @Test
    @DisplayName("Given a new statement misc handler, when isPoolable is called, then it returns true by default")
    void shouldReturnPoolableTrueByDefault() throws Exception {
        var handler = new StatementMiscHandler(mockConfig(), new StatementLifecycleHandler());

        assertTrue(handler.isPoolable());
    }

    @Test
    @DisplayName("Given a statement misc handler, when poolable is toggled, then isPoolable returns the new value")
    void shouldStorePoolableFlag() throws Exception {
        var handler = new StatementMiscHandler(mockConfig(), new StatementLifecycleHandler());

        handler.setPoolable(false);
        assertFalse(handler.isPoolable());

        handler.setPoolable(true);
        assertTrue(handler.isPoolable());
    }

    @Test
    @DisplayName("Given an open statement misc handler, when no-op methods are called, then they do not throw")
    void shouldAllowNoOpMethodsWhenOpen() {
        var handler = new StatementMiscHandler(mockConfig(), new StatementLifecycleHandler());

        assertDoesNotThrow(() -> handler.setEscapeProcessing(false));
        assertDoesNotThrow(() -> handler.setCursorName("cursor_1"));
        assertDoesNotThrow(handler::cancel);
    }

    @Test
    @DisplayName("Given a statement misc handler, when getConnection is called, then it returns a non-null open connection")
    void shouldCreateConnection() throws Exception {
        var handler = new StatementMiscHandler(mockConfig(), new StatementLifecycleHandler());

        try (var connection = handler.getConnection()) {
            assertNotNull(connection);
            assertFalse(connection.isClosed());
        }
    }

    @Test
    @DisplayName("Given a closed lifecycle, when guarded methods are called, then they throw SQLException")
    void shouldFailOnGuardedMethodsWhenStatementIsClosed() throws Exception {
        var lifecycle = new StatementLifecycleHandler();
        lifecycle.close();
        var handler = new StatementMiscHandler(mockConfig(), lifecycle);

        assertThrows(SQLException.class, () -> handler.setPoolable(true));
        assertThrows(SQLException.class, handler::isPoolable);
        assertThrows(SQLException.class, () -> handler.setEscapeProcessing(true));
        assertThrows(SQLException.class, () -> handler.setCursorName("cursor_1"));
        assertThrows(SQLException.class, handler::getConnection);
    }

    private static MockConfig mockConfig() {
        return new MockConfig(new MockConfig.MockServer("127.0.0.1", 50051), new Properties());
    }
}

