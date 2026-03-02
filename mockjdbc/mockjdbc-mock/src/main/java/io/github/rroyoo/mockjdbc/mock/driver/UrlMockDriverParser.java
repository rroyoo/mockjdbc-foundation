package io.github.rroyoo.mockjdbc.mock.driver;

import io.github.rroyoo.mockjdbc.mock.connection.MockConnectionProperties;

import java.util.Arrays;
import java.util.Properties;
import java.util.function.Consumer;
import java.util.regex.Pattern;

final class UrlMockDriverParser implements UrlDriverParser {

    private static final String URL_REGEX_PATTERN = "^jdbc:mock://(?<host>[\\w.-]+):(?<port>\\d+)(/\\?(?<params>.*))?$";
    private static final String URL_REGEX_PATTERN_GROUP_HOST = "host";
    private static final String URL_REGEX_PATTERN_GROUP_PARAMS = "params";
    private static final String URL_REGEX_PATTERN_GROUP_PORT = "port";
    private static final Pattern URL_REGEX = Pattern.compile(URL_REGEX_PATTERN);
    private static final String PARAMETER_SEPARATOR = ";";
    private static final String KEY_VALUE_SEPARATOR = "=";
    private static final int KEY_VALUE_SIZE = 2;

    @Override
    public Properties getProperties(String url) {
        var matcher = URL_REGEX.matcher(url);

        if (!matcher.matches()) {
            return null;
        }

        var properties = new Properties();

        addHost(matcher.group(URL_REGEX_PATTERN_GROUP_HOST), properties);
        addPort(matcher.group(URL_REGEX_PATTERN_GROUP_PORT), properties);
        addParams(matcher.group(URL_REGEX_PATTERN_GROUP_PARAMS), properties);

        return properties;
    }

    private void addHost(String host, Properties properties) {
        if(host != null && !host.isEmpty()) {
            properties.setProperty(MockConnectionProperties.HOST.getKey(), host);
        }
    }

    private void addPort(String port, Properties properties) {
        if(port != null && !port.isEmpty()) {
            properties.setProperty(MockConnectionProperties.PORT.getKey(), port);
        }
    }

    private void addParams(String params, Properties properties) {
        if(params == null || params.isEmpty()) {
            return;
        }

        Consumer<String> paramMapper = param -> {
            var keyValue = param.split(KEY_VALUE_SEPARATOR, KEY_VALUE_SIZE);
            if (keyValue.length == KEY_VALUE_SIZE) {
                properties.setProperty(keyValue[0], keyValue[1]);
            }
        };

        Arrays.stream(params.split(PARAMETER_SEPARATOR)).sequential().forEach(paramMapper);
    }



    @Override
    public boolean accepts(String url) {
        return URL_REGEX.matcher(url).matches();
    }
}
