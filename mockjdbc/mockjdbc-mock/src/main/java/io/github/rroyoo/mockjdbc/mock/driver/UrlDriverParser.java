package io.github.rroyoo.mockjdbc.mock.driver;

import java.util.Properties;

public interface UrlDriverParser {

    Properties getProperties(String url);

    boolean accepts(String url);
}
