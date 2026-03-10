package io.github.rroyoo.mockjdbc.mock.driver;

import java.net.MalformedURLException;
import java.util.Properties;
import java.util.regex.Pattern;

final class URLParser {

    // Accepted formats:
    // - jdbc:mock://localhost:8080
    // - jdbc:mock://localhost:8080/?param1=val1&paramN=valN
    static final String URL_PATTERN_EXPRESSION =
            "^jdbc:mock://(?<host>[a-zA-Z0-9.-]+):(?<port>\\d{1,5})(?:/\\?(?<params>[a-zA-Z0-9._~-]+=[^&=]+(?:&[a-zA-Z0-9._~-]+=[^&=]+)*))?$";

    private static final Pattern URL_PATTERN = Pattern.compile(URL_PATTERN_EXPRESSION);

    private URLParser() {}

    static MockConfig parse(String url) throws MalformedURLException {
        return parse(url, null);
    }

    static MockConfig parse(String url, Properties externalProperties) throws MalformedURLException {
        var matcher = URL_PATTERN.matcher(url);

        if(matcher.matches()) {
            var host = matcher.group("host");
            var port = Integer.parseInt(matcher.group("port"));
            var params = matcher.group("params");

            var properties = new Properties();

            if(externalProperties != null) {
                properties.putAll(externalProperties);
            }

            if(params != null) {
                properties.putAll(parseProperties(params));
            }

            return new MockConfig(new MockConfig.MockServer(host, port), properties);
        } else {
            throw new MalformedURLException(String.format("URL does not match expected format: %s", url));
        }
    }

    private static Properties parseProperties(String parameters) {
        var properties = new Properties();

        for(var param : parameters.split("&")) {
            var keyValue = param.split("=", 2);
            if(keyValue.length == 2) {
                properties.setProperty(keyValue[0], keyValue[1]);
            }
        }

        return properties;
    }
}
