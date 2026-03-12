package io.github.rroyoo.mockjdbc.mock.connection;

import io.github.rroyoo.mockjdbc.mock.driver.MockConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConnectionFactoryTest {

    @Test
    @DisplayName("Given a MockConfig, when creating a connection, then it returns a valid connection")
    void shouldCreateConnectionFromMockConfig() {
        // Act
        var connection = assertDoesNotThrow(() -> ConnectionFactory.create(mockConfig()));

        // Assert
        assertNotNull(connection);
    }

    @Test
    @DisplayName("Given a null MockConfig, when creating a connection, then it throws NullPointerException")
    void shouldThrowNullPointerExceptionWhenMockConfigIsNull() {
        // Act + Assert
        assertThrows(IllegalArgumentException.class, () -> ConnectionFactory.create(null));
    }

    @Test
    @DisplayName("Given a new connection, when isClosed is called, then it returns false")
    void shouldReturnNotClosedForNewConnection() throws Exception {
        // Arrange
        var connection = ConnectionFactory.create(mockConfig());

        // Act + Assert
        assertFalse(connection.isClosed());
    }

    @Test
    @DisplayName("Given an open connection, when close is called, then isClosed returns true")
    void shouldReturnClosedAfterCloseIsCalled() throws Exception {
        // Arrange
        var connection = ConnectionFactory.create(mockConfig());

        // Act
        connection.close();

        // Assert
        assertTrue(connection.isClosed());
    }

    private static MockConfig mockConfig() {
        var properties = new Properties();
        properties.setProperty("keepAliveTime", "60");
        return new MockConfig(new MockConfig.MockServer("localhost", 50051), properties);
    }
}
