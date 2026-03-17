package io.github.rroyoo.mockjdbc.mock.connection;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNull;

class NullResultHandlerTest {

    @Test
    @DisplayName("Given the handler, when handle is called, then it returns null")
    void shouldReturnNull() throws Exception {
        assertNull(NullResultHandler.handle());
    }
}

