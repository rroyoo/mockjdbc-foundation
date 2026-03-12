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

    @Test
    @DisplayName("Given a new connection, when getAutoCommit is called, then it returns true by default")
    void shouldReturnAutoCommitTrueByDefault() throws Exception {
        // Arrange
        var connection = ConnectionFactory.create(mockConfig());

        // Act + Assert
        assertTrue(connection.getAutoCommit());
    }

    @Test
    @DisplayName("Given a connection, when setAutoCommit is set to false, then getAutoCommit returns false")
    void shouldReturnFalseAfterAutoCommitIsDisabled() throws Exception {
        // Arrange
        var connection = ConnectionFactory.create(mockConfig());

        // Act
        connection.setAutoCommit(false);

        // Assert
        assertFalse(connection.getAutoCommit());
    }

    @Test
    @DisplayName("Given a connection with autoCommit disabled, when setAutoCommit is set to true, then getAutoCommit returns true")
    void shouldReturnTrueAfterAutoCommitIsReEnabled() throws Exception {
        // Arrange
        var connection = ConnectionFactory.create(mockConfig());
        connection.setAutoCommit(false);

        // Act
        connection.setAutoCommit(true);

        // Assert
        assertTrue(connection.getAutoCommit());
    }

    @Test
    @DisplayName("Given a new connection, when isReadOnly is called, then it returns false by default")
    void shouldReturnReadOnlyFalseByDefault() throws Exception {
        // Arrange
        var connection = ConnectionFactory.create(mockConfig());

        // Act + Assert
        assertFalse(connection.isReadOnly());
    }

    @Test
    @DisplayName("Given a connection, when setReadOnly is set to true, then isReadOnly returns true")
    void shouldReturnTrueAfterReadOnlyIsEnabled() throws Exception {
        // Arrange
        var connection = ConnectionFactory.create(mockConfig());

        // Act
        connection.setReadOnly(true);

        // Assert
        assertTrue(connection.isReadOnly());
    }

    @Test
    @DisplayName("Given a read-only connection, when setReadOnly is set to false, then isReadOnly returns false")
    void shouldReturnFalseAfterReadOnlyIsDisabled() throws Exception {
        // Arrange
        var connection = ConnectionFactory.create(mockConfig());
        connection.setReadOnly(true);

        // Act
        connection.setReadOnly(false);

        // Assert
        assertFalse(connection.isReadOnly());
    }

    private static MockConfig mockConfig() {
        var properties = new Properties();
        properties.setProperty("keepAliveTime", "60");
        return new MockConfig(new MockConfig.MockServer("localhost", 50051), properties);
    }
}
