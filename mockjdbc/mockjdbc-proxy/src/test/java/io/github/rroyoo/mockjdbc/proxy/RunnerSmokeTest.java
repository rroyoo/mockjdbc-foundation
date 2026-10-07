package io.github.rroyoo.mockjdbc.proxy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class RunnerSmokeTest {

    @Test
    @DisplayName("Given the proxy module, when JdbcExecutionCapture is instantiated, then it is ready for capture")
    void shouldInstantiateCapture() {
        try (var capture = new JdbcExecutionCapture(
                "default-datasource",
                event -> {},
                MockedQueryEventProducer.fromConsumer(q -> {}),
                AsyncDispatchConfig.defaults())) {
            assertNotNull(capture);
        }
    }

    @Test
    @DisplayName("Given the proxy module, when JdbcAgentRegistry is used, then null lookup is safe and returns null")
    void shouldHandleNullLookupSafely() {
        // lookup(null) is a documented no-op that returns null — the important thing is no NPE
        assertNotNull(JdbcAgentRegistry.class.getName());
        org.junit.jupiter.api.Assertions.assertNull(JdbcAgentRegistry.lookup(null));
    }
}
