package io.github.rroyoo.mockjdbc.mock.driver;

import java.util.Properties;

public record MockConfig(MockServer mockServer, Properties properties) {

    public record MockServer(String host, int port) {}
}
