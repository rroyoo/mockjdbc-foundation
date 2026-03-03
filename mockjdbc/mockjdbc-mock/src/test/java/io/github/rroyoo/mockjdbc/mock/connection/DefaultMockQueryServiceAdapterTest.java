package io.github.rroyoo.mockjdbc.mock.connection;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DefaultMockQueryServiceAdapterTest {

    @Test
    @DisplayName("should throw when host property is missing")
    void shouldRejectMissingHostProperty() {
        Properties props = new Properties();
        props.setProperty("port", "50051");

        assertThrows(Exception.class, () -> new DefaultMockQueryServiceAdapter(props));
    }

    @Test
    @DisplayName("should throw when port property is missing")
    void shouldRejectMissingPortProperty() {
        Properties props = new Properties();
        props.setProperty("host", "localhost");

        assertThrows(Exception.class, () -> new DefaultMockQueryServiceAdapter(props));
    }

    @Test
    @DisplayName("should create adapter with valid host and port")
    void shouldCreateAdapterWithValidProperties() throws Exception {
        Properties props = new Properties();
        props.setProperty("host", "localhost");
        props.setProperty("port", "50051");

        DefaultMockQueryServiceAdapter adapter = new DefaultMockQueryServiceAdapter(props);

        assertNotNull(adapter);
    }

    @Test
    @DisplayName("should apply keep-alive settings from properties")
    void shouldApplyKeepAliveSettings() throws Exception {
        Properties props = new Properties();
        props.setProperty("host", "localhost");
        props.setProperty("port", "50051");
        props.setProperty("keepAliveTime", "60");
        props.setProperty("keepAliveTimeUnit", "SECONDS");

        DefaultMockQueryServiceAdapter adapter = new DefaultMockQueryServiceAdapter(props);

        assertNotNull(adapter);
        // Adapter should be created without throwing exception
    }

    @Test
    @DisplayName("should close adapter without error")
    void shouldCloseGracefully() throws Exception {
        Properties props = new Properties();
        props.setProperty("host", "localhost");
        props.setProperty("port", "50051");

        DefaultMockQueryServiceAdapter adapter = new DefaultMockQueryServiceAdapter(props);
        adapter.close();
        // Should complete without exception
    }
}

