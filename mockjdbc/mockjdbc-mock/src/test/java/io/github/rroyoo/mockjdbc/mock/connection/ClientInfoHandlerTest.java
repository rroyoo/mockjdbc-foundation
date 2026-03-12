package io.github.rroyoo.mockjdbc.mock.connection;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClientInfoHandlerTest {

    @Test
    @DisplayName("Given a new handler, when getClientInfo is called, then it returns empty properties")
    void shouldReturnEmptyPropertiesByDefault() throws Exception {
        var handler = new ClientInfoHandler();
        assertTrue(handler.getClientInfo().isEmpty());
    }

    @Test
    @DisplayName("Given a handler, when setClientInfo with key/value is called, then getClientInfo returns the value")
    void shouldReturnValueAfterKeyValueIsSet() throws Exception {
        var handler = new ClientInfoHandler();
        handler.setClientInfo("ApplicationName", "myApp");
        assertEquals("myApp", handler.getClientInfo("ApplicationName"));
    }

    @Test
    @DisplayName("Given a handler, when setClientInfo with null value is called, then the key is removed")
    void shouldRemoveKeyWhenValueIsNull() throws Exception {
        var handler = new ClientInfoHandler();
        handler.setClientInfo("ApplicationName", "myApp");
        handler.setClientInfo("ApplicationName", null);
        assertNull(handler.getClientInfo("ApplicationName"));
    }

    @Test
    @DisplayName("Given a handler, when setClientInfo with Properties is called, then getClientInfo returns all values")
    void shouldReturnAllValuesAfterPropertiesAreSet() throws Exception {
        var handler = new ClientInfoHandler();
        var props = new Properties();
        props.setProperty("ApplicationName", "myApp");
        props.setProperty("ClientUser", "bob");

        handler.setClientInfo(props);

        assertEquals("myApp", handler.getClientInfo("ApplicationName"));
        assertEquals("bob", handler.getClientInfo("ClientUser"));
    }

    @Test
    @DisplayName("Given a handler with existing info, when setClientInfo with Properties is called, then old entries are replaced")
    void shouldReplaceExistingInfoWhenPropertiesAreSet() throws Exception {
        var handler = new ClientInfoHandler();
        handler.setClientInfo("OldKey", "oldValue");

        var props = new Properties();
        props.setProperty("NewKey", "newValue");
        handler.setClientInfo(props);

        assertNull(handler.getClientInfo("OldKey"));
        assertEquals("newValue", handler.getClientInfo("NewKey"));
    }

    @Test
    @DisplayName("Given a handler, when getClientInfo for unknown key is called, then it returns null")
    void shouldReturnNullForUnknownKey() throws Exception {
        var handler = new ClientInfoHandler();
        assertNull(handler.getClientInfo("unknown"));
    }

    @Test
    @DisplayName("Given a handler, when getClientInfo is called, then it returns a Properties copy")
    void shouldReturnPropertiesCopyWithAllEntries() throws Exception {
        var handler = new ClientInfoHandler();
        handler.setClientInfo("ApplicationName", "myApp");

        var result = handler.getClientInfo();

        assertNotNull(result);
        assertEquals("myApp", result.getProperty("ApplicationName"));
    }
}

