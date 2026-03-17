package io.github.rroyoo.mockjdbc.mock.driver;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Properties;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MockDriverTest {

    private final MockDriver mockDriver = new MockDriver();

    @Test
    @DisplayName("Given a mock driver, when asking for major version, then it returns 1")
    void shouldReturnMajorVersionOne() {
        assertEquals(1, mockDriver.getMajorVersion());
    }

    @Test
    @DisplayName("Given a mock driver, when asking for minor version, then it returns 0")
    void shouldReturnMinorVersionZero() {
        assertEquals(0, mockDriver.getMinorVersion());
    }

    @Test
    @DisplayName("Given a valid mock URL, when acceptsURL is called, then it returns true")
    void shouldAcceptValidUrl() throws Exception {
        assertTrue(mockDriver.acceptsURL("jdbc:mock://localhost:8080"));
    }

    @Test
    @DisplayName("Given an invalid URL, when acceptsURL is called, then it returns false")
    void shouldRejectInvalidUrl() throws Exception {
        assertFalse(mockDriver.acceptsURL("jdbc:mysql://localhost:8080"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "jdbc:mock://localhost:8080",
            "jdbc:mock://127.0.0.1:50051/?keepAliveTime=60&keepAliveTimeUnit=SECONDS"
    })
    @DisplayName("Given valid mock URLs, when acceptsURL is called, then it returns true")
    void shouldAcceptAllSupportedMockUrlFormats(String url) {
        assertTrue(mockDriver.acceptsURL(url));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "jdbc:mock://localhost",
            "jdbc:mock://localhost:abc",
            "jdbc:mock://localhost:8080?missingSlashBeforeQuery"
    })
    @DisplayName("Given malformed mock URLs, when acceptsURL is called, then it returns false")
    void shouldRejectMalformedMockUrls(String url) {
        assertFalse(mockDriver.acceptsURL(url));
    }

    @Test
    @DisplayName("Given a null URL, when acceptsURL is called, then it returns false")
    void shouldRejectNullUrl() {
        assertFalse(mockDriver.acceptsURL(null));
    }

    @Test
    @DisplayName("Given a mock driver, when getParentLogger is called, then it returns Logger global")
    void shouldReturnGlobalParentLogger() throws Exception {
        assertSame(Logger.getGlobal(), mockDriver.getParentLogger());
    }

    @Test
    @DisplayName("Given a mock driver, when jdbcCompliant is called, then it returns false")
    void shouldReturnNonJdbcCompliant() {
        assertFalse(mockDriver.jdbcCompliant());
    }

    @Test
    @DisplayName("Given a mock driver, when getPropertyInfo is called, then it returns an empty array")
    void shouldReturnEmptyPropertyInfo() throws Exception {
        var propertyInfo = mockDriver.getPropertyInfo("jdbc:mock://localhost:8080", new java.util.Properties());

        assertEquals(0, propertyInfo.length);
    }

    @Test
    @DisplayName("Given a valid mock URL, when connect is called, then it returns a connection")
    void shouldCreateConnectionForValidMockUrl() throws Exception {
        var connection = mockDriver.connect("jdbc:mock://localhost:50051", new Properties());

        assertNotNull(connection);
    }

    @Test
    @DisplayName("Given a non-mock URL, when connect is called, then it returns null")
    void shouldReturnNullForUnsupportedUrlOnConnect() throws Exception {
        var connection = mockDriver.connect("jdbc:mysql://localhost:3306", new Properties());

        assertNull(connection);
    }
}