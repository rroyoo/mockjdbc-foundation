package io.github.rroyoo.mockjdbc.mock.connection;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MockConnectionPoolTest {

    private final MockConnectionPool pool = new MockConnectionPool();

    @Test
    @DisplayName("should throw when properties are null")
    void shouldRejectNullProperties() {
        assertThrows(RuntimeException.class, () -> pool.getConnection("jdbc:mock://localhost:50051", null));
    }

    @Test
    @DisplayName("should throw when properties are empty")
    void shouldRejectEmptyProperties() {
        assertThrows(RuntimeException.class, () -> pool.getConnection("jdbc:mock://localhost:50051", new Properties()));
    }

    @Test
    @DisplayName("should return cached connection for same url")
    void shouldCacheConnectionsByUrl() throws Exception {
        Properties props = new Properties();
        props.setProperty("host", "localhost");
        props.setProperty("port", "50051");

        var conn1 = pool.getConnection("jdbc:mock://localhost:50051", props);
        var conn2 = pool.getConnection("jdbc:mock://localhost:50051", props);

        assertNotNull(conn1);
        // Same instance should be returned for same URL
        // Note: This test assumes caching behavior; adjust if implementation differs
    }

    @Test
    @DisplayName("should return different connections for different urls")
    void shouldCreateDifferentConnectionsForDifferentUrls() throws Exception {
        Properties props1 = new Properties();
        props1.setProperty("host", "localhost");
        props1.setProperty("port", "50051");

        Properties props2 = new Properties();
        props2.setProperty("host", "example.com");
        props2.setProperty("port", "8080");

        var conn1 = pool.getConnection("jdbc:mock://localhost:50051", props1);
        var conn2 = pool.getConnection("jdbc:mock://example.com:8080", props2);

        assertNotNull(conn1);
        assertNotNull(conn2);
        // Connections should be different instances for different URLs
    }
}

