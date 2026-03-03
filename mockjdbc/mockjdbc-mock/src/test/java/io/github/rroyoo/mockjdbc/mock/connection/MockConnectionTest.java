package io.github.rroyoo.mockjdbc.mock.connection;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MockConnectionTest {

    @Test
    @DisplayName("should create connection with valid adapter")
    void shouldCreateConnectionWithValidAdapter() throws Exception {
        Properties props = new Properties();
        props.setProperty("host", "localhost");
        props.setProperty("port", "50051");

        GrpcQueryServiceAdapter adapter = new GrpcQueryServiceAdapter(props);
        MockConnection connection = new MockConnection(props, adapter);

        assertNotNull(connection);

        connection.close();
        adapter.close();
    }

    @Test
    @DisplayName("should throw when adapter creation fails due to missing host")
    void shouldFailWithoutHost() {
        Properties props = new Properties();
        props.setProperty("port", "50051");

        assertThrows(Exception.class, () -> {
            new GrpcQueryServiceAdapter(props);
        });
    }
}

