package io.github.rroyoo.mockjdbc.proxy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.logging.Level;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

class LoggingQueryExecutionEventListenerTest {

    private final LoggingQueryExecutionEventListener listener = new LoggingQueryExecutionEventListener();

    @ParameterizedTest(name = "{index} => sql={0}")
    @CsvSource({
            "SELECT 1",
            "SELECT * FROM users WHERE id = ?",
            "INSERT INTO log VALUES (?, ?)"
    })
    @DisplayName("should log query execution event")
    void shouldLogEvent(String sql) {
        QueryExecutionEvent event = new QueryExecutionEvent(sql, java.util.List.of(), null, 100L, true, null);

        assertDoesNotThrow(() -> listener.onQueryExecutionEvent(event));
        // Listener should not throw and should log the event
    }

    @Test
    @DisplayName("should handle event with exception")
    void shouldLogEventWithException() {
        RuntimeException ex = new RuntimeException("Query failed");
        QueryExecutionEvent event = new QueryExecutionEvent(
                "SELECT 1",
                java.util.List.of(),
                null,
                150L,
                false,
                ex
        );

        assertDoesNotThrow(() -> listener.onQueryExecutionEvent(event));
        // Listener should handle events with exceptions
    }

    @Test
    @DisplayName("should handle null result")
    void shouldLogEventWithNullResult() {
        QueryExecutionEvent event = new QueryExecutionEvent(
                "UPDATE table SET col = ?",
                java.util.List.of("value"),
                null,  // null result
                50L,
                true,
                null
        );

        assertDoesNotThrow(() -> listener.onQueryExecutionEvent(event));
    }
}

