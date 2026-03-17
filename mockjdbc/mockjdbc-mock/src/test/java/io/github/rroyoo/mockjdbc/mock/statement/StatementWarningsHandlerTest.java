package io.github.rroyoo.mockjdbc.mock.statement;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNull;

class StatementWarningsHandlerTest {

    @Test
    @DisplayName("Given a new statement warnings handler, when getWarnings is called, then it returns null")
    void shouldReturnNullWarningsByDefault() throws Exception {
        var handler = new StatementWarningsHandler();

        assertNull(handler.getWarnings());
    }

    @Test
    @DisplayName("Given a statement warnings handler, when clearWarnings is called, then warnings remain null")
    void shouldKeepWarningsNullAfterClear() throws Exception {
        var handler = new StatementWarningsHandler();

        handler.clearWarnings();

        assertNull(handler.getWarnings());
    }
}

