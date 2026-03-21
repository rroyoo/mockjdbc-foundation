package io.github.rroyoo.mockjdbc.wiremock;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class WireMockKafkaBridgeExtensionTest {

    @Test
    @DisplayName("Given extension lifecycle, start/stop delegates to Kafka consumer")
    void shouldDelegateLifecycleToConsumer() {
        var consumer = mock(KafkaMappingConsumer.class);
        var extension = new WireMockKafkaBridgeExtension("wiremock-kafka-bridge", consumer);

        extension.start();
        extension.stop();

        assertEquals("wiremock-kafka-bridge", extension.getName());
        verify(consumer).start();
        verify(consumer).close();
    }
}

