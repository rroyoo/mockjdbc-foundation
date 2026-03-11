package io.github.rroyoo.mockjdbc.mock.connection;

import io.github.rroyoo.mockjdbc.mock.driver.MockConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ConnectionFactoryTest {

    @Test
    @DisplayName("Given a MockConfig, when creating a connection, then it returns a valid connection")
    void shouldCreateConnectionFromMockConfig() {
        // Arrange
        var properties = new Properties();
        properties.setProperty("keepAliveTime", "60");
        var mockConfig = new MockConfig(new MockConfig.MockServer("localhost", 50051), properties);

        // Act
        var connection = assertDoesNotThrow(() -> ConnectionFactory.create(mockConfig));

        // Assert
        assertNotNull(connection);
    }

    @Test
    @DisplayName("Given a null MockConfig, when creating a connection, then it throws NullPointerException")
    void shouldThrowNullPointerExceptionWhenMockConfigIsNull() {
        // Act + Assert
        assertThrows(IllegalArgumentException.class, () -> ConnectionFactory.create(null));
    }
}
