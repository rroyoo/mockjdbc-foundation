package io.github.rroyoo.mockjdbc.mock.connection;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.SQLWarning;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class WarningsHandlerTest {

    @Test
    @DisplayName("Given a new handler, when getWarnings is called, then it returns null")
    void shouldReturnNullWarningsByDefault() throws Exception {
        var handler = new WarningsHandler();
        assertNull(handler.getWarnings());
    }

    @Test
    @DisplayName("Given a handler with a warning added, when getWarnings is called, then it returns the warning")
    void shouldReturnWarningAfterItIsAdded() throws Exception {
        var handler = new WarningsHandler();
        var warning = new SQLWarning("test warning");

        handler.addWarning(warning);

        assertNotNull(handler.getWarnings());
        assertEquals("test warning", handler.getWarnings().getMessage());
    }

    @Test
    @DisplayName("Given a handler with multiple warnings, when getWarnings is called, then they are chained")
    void shouldChainMultipleWarnings() throws Exception {
        var handler = new WarningsHandler();
        handler.addWarning(new SQLWarning("first"));
        handler.addWarning(new SQLWarning("second"));

        var first = handler.getWarnings();
        assertNotNull(first);
        assertEquals("first", first.getMessage());
        assertNotNull(first.getNextWarning());
        assertEquals("second", first.getNextWarning().getMessage());
    }

    @Test
    @DisplayName("Given a handler with warnings, when clearWarnings is called, then getWarnings returns null")
    void shouldReturnNullAfterWarningsAreCleared() throws Exception {
        var handler = new WarningsHandler();
        handler.addWarning(new SQLWarning("test warning"));

        handler.clearWarnings();

        assertNull(handler.getWarnings());
    }
}

