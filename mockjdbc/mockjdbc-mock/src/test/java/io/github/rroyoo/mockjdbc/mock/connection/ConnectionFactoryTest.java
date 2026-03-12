package io.github.rroyoo.mockjdbc.mock.connection;

import io.github.rroyoo.mockjdbc.mock.driver.MockConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.ResultSet;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
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

    // -- setTransactionIsolation / getTransactionIsolation --

    @Test
    @DisplayName("Given a new connection, when getTransactionIsolation is called, then it returns TRANSACTION_READ_COMMITTED by default")
    void shouldReturnReadCommittedIsolationByDefault() throws Exception {
        var connection = ConnectionFactory.create(mockConfig());
        assertEquals(Connection.TRANSACTION_READ_COMMITTED, connection.getTransactionIsolation());
    }

    @Test
    @DisplayName("Given a connection, when setTransactionIsolation is set to SERIALIZABLE, then getTransactionIsolation returns SERIALIZABLE")
    void shouldReturnSerializableAfterIsolationIsSet() throws Exception {
        var connection = ConnectionFactory.create(mockConfig());
        connection.setTransactionIsolation(Connection.TRANSACTION_SERIALIZABLE);
        assertEquals(Connection.TRANSACTION_SERIALIZABLE, connection.getTransactionIsolation());
    }

    // -- setHoldability / getHoldability --

    @Test
    @DisplayName("Given a new connection, when getHoldability is called, then it returns CLOSE_CURSORS_AT_COMMIT by default")
    void shouldReturnCloseCursorsAtCommitHoldabilityByDefault() throws Exception {
        var connection = ConnectionFactory.create(mockConfig());
        assertEquals(ResultSet.CLOSE_CURSORS_AT_COMMIT, connection.getHoldability());
    }

    @Test
    @DisplayName("Given a connection, when setHoldability is set to HOLD_CURSORS_OVER_COMMIT, then getHoldability returns HOLD_CURSORS_OVER_COMMIT")
    void shouldReturnHoldCursorsAfterHoldabilityIsSet() throws Exception {
        var connection = ConnectionFactory.create(mockConfig());
        connection.setHoldability(ResultSet.HOLD_CURSORS_OVER_COMMIT);
        assertEquals(ResultSet.HOLD_CURSORS_OVER_COMMIT, connection.getHoldability());
    }

    // -- setCatalog / getCatalog --

    @Test
    @DisplayName("Given a new connection, when getCatalog is called, then it returns null by default")
    void shouldReturnNullCatalogByDefault() throws Exception {
        var connection = ConnectionFactory.create(mockConfig());
        assertNull(connection.getCatalog());
    }

    @Test
    @DisplayName("Given a connection, when setCatalog is called, then getCatalog returns the same value")
    void shouldReturnCatalogAfterItIsSet() throws Exception {
        var connection = ConnectionFactory.create(mockConfig());
        connection.setCatalog("my_catalog");
        assertEquals("my_catalog", connection.getCatalog());
    }

    // -- setSchema / getSchema --

    @Test
    @DisplayName("Given a new connection, when getSchema is called, then it returns null by default")
    void shouldReturnNullSchemaByDefault() throws Exception {
        var connection = ConnectionFactory.create(mockConfig());
        assertNull(connection.getSchema());
    }

    @Test
    @DisplayName("Given a connection, when setSchema is called, then getSchema returns the same value")
    void shouldReturnSchemaAfterItIsSet() throws Exception {
        var connection = ConnectionFactory.create(mockConfig());
        connection.setSchema("my_schema");
        assertEquals("my_schema", connection.getSchema());
    }

    // -- setNetworkTimeout / getNetworkTimeout --

    @Test
    @DisplayName("Given a new connection, when getNetworkTimeout is called, then it returns 0 by default")
    void shouldReturnZeroNetworkTimeoutByDefault() throws Exception {
        var connection = ConnectionFactory.create(mockConfig());
        assertEquals(0, connection.getNetworkTimeout());
    }

    @Test
    @DisplayName("Given a connection, when setNetworkTimeout is called, then getNetworkTimeout returns the same value")
    void shouldReturnNetworkTimeoutAfterItIsSet() throws Exception {
        var connection = ConnectionFactory.create(mockConfig());
        connection.setNetworkTimeout(null, 5000);
        assertEquals(5000, connection.getNetworkTimeout());
    }

    private static MockConfig mockConfig() {
        var properties = new Properties();
        properties.setProperty("keepAliveTime", "60");
        return new MockConfig(new MockConfig.MockServer("localhost", 50051), properties);
    }
}
