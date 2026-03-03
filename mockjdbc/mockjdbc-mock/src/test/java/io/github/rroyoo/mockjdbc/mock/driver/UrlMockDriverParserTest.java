package io.github.rroyoo.mockjdbc.mock.driver;

import io.github.rroyoo.mockjdbc.mock.connection.MockConnectionProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UrlMockDriverParserTest {

    private final UrlMockDriverParser parser = new UrlMockDriverParser();

    @ParameterizedTest(name = "{index} => url={0}")
    @ValueSource(strings = {
            "jdbc:mock://localhost:50051",
            "jdbc:mock://example.com:1234/?keepAliveTime=30;keepAliveTimeUnit=SECONDS"
    })
    @DisplayName("should accept valid mock JDBC urls")
    void shouldAcceptValidUrls(String url) {
        assertTrue(parser.accepts(url));
    }

    @ParameterizedTest(name = "{index} => url={0}")
    @ValueSource(strings = {
            "jdbc:postgresql://localhost:5432/db",
            "jdbc:mock://localhost",
            "jdbc:mock://localhost:port",
            "not-a-jdbc-url"
    })
    @DisplayName("should reject invalid mock JDBC urls")
    void shouldRejectInvalidUrls(String url) {
        assertFalse(parser.accepts(url));
        assertNull(parser.getProperties(url));
    }

    @ParameterizedTest(name = "{index} => host={1}, port={2}")
    @CsvSource({
            "jdbc:mock://localhost:50051,localhost,50051",
            "jdbc:mock://example.org:8080,example.org,8080"
    })
    @DisplayName("should parse host and port properties from valid url")
    void shouldParseCoreProperties(String url, String expectedHost, String expectedPort) {
        Properties properties = parser.getProperties(url);

        assertNotNull(properties);
        assertEquals(expectedHost, properties.getProperty(MockConnectionProperties.HOST.getKey()));
        assertEquals(expectedPort, properties.getProperty(MockConnectionProperties.PORT.getKey()));
    }

    @ParameterizedTest(name = "{index} => url={0}")
    @ValueSource(strings = {
            "jdbc:mock://localhost:50051/?keepAliveTime=30;keepAliveTimeUnit=SECONDS",
            "jdbc:mock://localhost:50051/?idleTimeout=10;idleTimeoutTimeUnit=MINUTES;badParam"
    })
    @DisplayName("should parse optional key-value params and ignore malformed fragments")
    void shouldParseOptionalParams(String url) {
        Properties properties = parser.getProperties(url);

        assertNotNull(properties);
        assertEquals("localhost", properties.getProperty(MockConnectionProperties.HOST.getKey()));
        assertEquals("50051", properties.getProperty(MockConnectionProperties.PORT.getKey()));
    }
}
