package io.github.rroyoo.mockjdbc.mock.connection;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import java.lang.reflect.Method;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class GenericVoidMethodHandlerTest {

    @Test
    @DisplayName(
            "Given any method metadata, when handle is called, then it performs a no-op without throwing")
    void shouldPerformNoOpWithoutThrowing() throws Exception {
        Method method = GenericVoidMethodHandlerTest.class.getDeclaredMethod("sampleMethod");

        assertDoesNotThrow(() -> GenericVoidMethodHandler.handle(method));
    }

    private void sampleMethod() {
        // no-op
    }
}
