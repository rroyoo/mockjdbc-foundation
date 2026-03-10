package io.github.rroyoo.mockjdbc.mock.driver;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.MalformedURLException;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class URLParserTest {

    @Test
    @DisplayName("Given a URL string, when parsing, then it returns a MockConfig with correctly parsed values")
    void shouldParseUrlStringIntoMockConfig() {
        // Arrange
        var url = "jdbc:mock://localhost:50051/?keepAliveTime=60&keepAliveTimeUnit=SECONDS";

        // Act
        var config = assertDoesNotThrow(() -> URLParser.parse(url));

        // Assert
        assertNotNull(config);
        assertNotNull(config.mockServer());
        assertEquals("localhost", config.mockServer().host());
        assertEquals(50051, config.mockServer().port());
        assertEquals("60", config.properties().getProperty("keepAliveTime"));
        assertEquals("SECONDS", config.properties().getProperty("keepAliveTimeUnit"));
    }

    @Test
    @DisplayName("Given a valid URL without query params, when parsing, then it returns a MockConfig")
    void shouldParseUrlWithoutQueryParamsIntoMockConfig() {
        // Arrange
        var url = "jdbc:mock://localhost:50051";

        // Act
        var config = assertDoesNotThrow(() -> URLParser.parse(url));

        // Assert
        assertNotNull(config);
        assertNotNull(config.mockServer());
        assertEquals("localhost", config.mockServer().host());
        assertEquals(50051, config.mockServer().port());
        assertNotNull(config.properties());
        assertEquals(0, config.properties().size());
    }

    @Test
    @DisplayName("Given an invalid URL, when parsing, then it throws MalformedURLException")
    void shouldThrowMalformedURLExceptionForInvalidUrl() {
        // Arrange
        var invalidUrl = "jdbc:mock://localhost";

        // Act + Assert
        assertThrows(MalformedURLException.class, () -> URLParser.parse(invalidUrl));
    }

    @Test
    @DisplayName("Given external properties and same key in query params, when parsing, then query value overrides external value")
    void shouldOverrideExternalPropertyWithQueryParamValue() {
        // Arrange
        var url = "jdbc:mock://localhost:50051/?keepAliveTime=60&keepAliveTimeUnit=SECONDS";
        var externalProperties = new java.util.Properties();
        externalProperties.setProperty("keepAliveTime", "30");
        externalProperties.setProperty("region", "eu-west-1");

        // Act
        var config = assertDoesNotThrow(() -> URLParser.parse(url, externalProperties));

        // Assert
        assertEquals("60", config.properties().getProperty("keepAliveTime"));
        assertEquals("SECONDS", config.properties().getProperty("keepAliveTimeUnit"));
        assertEquals("eu-west-1", config.properties().getProperty("region"));
    }
}
