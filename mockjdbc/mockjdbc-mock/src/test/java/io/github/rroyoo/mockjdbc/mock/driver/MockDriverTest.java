package io.github.rroyoo.mockjdbc.mock.driver;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.sql.DriverPropertyInfo;
import java.sql.SQLException;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MockDriverTest {

    private final MockDriver driver = new MockDriver();

    @ParameterizedTest(name = "{index} => url={0}")
    @ValueSource(strings = {
            "jdbc:mock://localhost:50051",
            "jdbc:mock://example.com:8080/?keepAliveTime=30"
    })
    @DisplayName("should accept valid mock JDBC urls")
    void shouldAcceptValidUrls(String url) {
        assertTrue(driver.acceptsURL(url));
    }

    @ParameterizedTest(name = "{index} => url={0}")
    @ValueSource(strings = {
            "jdbc:postgresql://localhost:5432/db",
            "jdbc:mysql://localhost:3306/db",
            "not-a-jdbc-url"
    })
    @DisplayName("should reject non-mock JDBC urls")
    void shouldRejectNonMockUrls(String url) {
        assertTrue(!driver.acceptsURL(url));
    }

    @ParameterizedTest(name = "{index} => url={0}")
    @ValueSource(strings = {
            "jdbc:mock://localhost:50051",
            "jdbc:mock://example.org:1234/?keepAliveTime=60;keepAliveTimeUnit=SECONDS"
    })
    @DisplayName("should connect to valid mock JDBC url with properties")
    void shouldConnectToValidUrl(String url) throws SQLException {
        try (var connection = driver.connect(url, new Properties())) {
            assertNotNull(connection);
        }
    }

    @DisplayName("should throw exception when connecting to invalid url")
    @ParameterizedTest
    @ValueSource(strings = {"jdbc:postgresql://localhost:5432/db"})
    void shouldRejectInvalidUrlOnConnect(String url) throws SQLException {
        assertThrows(SQLException.class, () -> driver.connect(url, new Properties()));
    }

    @DisplayName("should report correct version")
    @ParameterizedTest
    @ValueSource(ints = {1})
    void shouldReportMajorVersion(int expectedMajor) {
        assertEquals(expectedMajor, driver.getMajorVersion());
    }

    @DisplayName("should report JDBC non-compliant")
    @ParameterizedTest
    @ValueSource(booleans = {false})
    void shouldNotBeJdbcCompliant(boolean expected) {
        assertEquals(expected, driver.jdbcCompliant());
    }
}

