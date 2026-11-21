package io.github.rroyoo.mockjdbc.mock.connection;

import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class NullResultHandlerTest {

    @Test
    @DisplayName("Given the handler, when handle is called, then it returns null")
    void shouldReturnNull() throws Exception {
        assertNull(NullResultHandler.handle());
    }
}
