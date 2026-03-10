package io.github.rroyoo.mockjdbc.mock.driver;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

    @Test
    @DisplayName("Given a mock driver, when getParentLogger is called, then it returns Logger global")
    void shouldReturnGlobalParentLogger() throws Exception {
        assertSame(Logger.getGlobal(), mockDriver.getParentLogger());
    }
}