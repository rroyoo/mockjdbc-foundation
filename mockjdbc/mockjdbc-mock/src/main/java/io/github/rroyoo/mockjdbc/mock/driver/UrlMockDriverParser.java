package io.github.rroyoo.mockjdbc.mock.driver;

import io.github.rroyoo.mockjdbc.mock.connection.MockConnectionProperties;

import java.util.Properties;
import java.util.regex.Pattern;

final class UrlMockDriverParser implements UrlDriverParser {

    private static final String URL_REGEX_PATTERN = "^jdbc:mock://(?<host>[\\w.-]+):(?<port>\\d+)(/\\?(?<params>.*))?$";
    private static final String GROUP_HOST = "host";
    private static final String GROUP_PORT = "port";
    private static final String GROUP_PARAMS = "params";
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

        putIfPresent(properties, MockConnectionProperties.HOST.getKey(), matcher.group(GROUP_HOST));
        putIfPresent(properties, MockConnectionProperties.PORT.getKey(), matcher.group(GROUP_PORT));
        addParams(matcher.group(GROUP_PARAMS), properties);

        return properties;
    }

    private void putIfPresent(Properties properties, String key, String value) {
        if (value != null && !value.isEmpty()) {
            properties.setProperty(key, value);
        }
    }

    private void addParams(String params, Properties properties) {
        if (params == null || params.isEmpty()) {
            return;
        }

        for (String param : params.split(PARAMETER_SEPARATOR)) {
            var keyValue = param.split(KEY_VALUE_SEPARATOR, KEY_VALUE_SIZE);

            if (keyValue.length == KEY_VALUE_SIZE && !keyValue[0].isBlank()) {
                properties.setProperty(keyValue[0], keyValue[1]);
            }
        }
    }

    @Override
    public boolean accepts(String url) {
        return URL_REGEX.matcher(url).matches();
    }
}
