package io.github.rroyoo.mockjdbc.proxy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class RunnerSmokeTest {

    @Test
    @DisplayName("Given the listener module, when instantiated, then listener is ready")
    void shouldInstantiateListener() {
        assertNotNull(new JdbcQueryCaptureListener());
    }
}

