package io.github.rroyoo.mockjdbc.mock.connection;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.ResultSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigHandlerTest {

    @Test
    @DisplayName("Given a new handler, when reading defaults, then JDBC-like default config values are returned")
    void shouldReturnJdbcDefaults() throws Exception {
        var handler = new ConfigHandler();

        assertFalse(handler.isReadOnly());
        assertEquals(Connection.TRANSACTION_READ_COMMITTED, handler.getTransactionIsolation());
        assertEquals(ResultSet.CLOSE_CURSORS_AT_COMMIT, handler.getHoldability());
        assertNull(handler.getCatalog());
        assertNull(handler.getSchema());
        assertEquals(0, handler.getNetworkTimeout());
    }

    @Test
    @DisplayName("Given a handler, when values are configured, then getters return the configured values")
    void shouldStoreAndReturnConfiguredValues() throws Exception {
        var handler = new ConfigHandler();

        handler.setReadOnly(true);
        handler.setTransactionIsolation(Connection.TRANSACTION_SERIALIZABLE);
        handler.setHoldability(ResultSet.HOLD_CURSORS_OVER_COMMIT);
        handler.setCatalog("catalog_a");
        handler.setSchema("schema_a");
        handler.setNetworkTimeout(null, 5000);

        assertTrue(handler.isReadOnly());
        assertEquals(Connection.TRANSACTION_SERIALIZABLE, handler.getTransactionIsolation());
        assertEquals(ResultSet.HOLD_CURSORS_OVER_COMMIT, handler.getHoldability());
        assertEquals("catalog_a", handler.getCatalog());
        assertEquals("schema_a", handler.getSchema());
        assertEquals(5000, handler.getNetworkTimeout());
    }
}

