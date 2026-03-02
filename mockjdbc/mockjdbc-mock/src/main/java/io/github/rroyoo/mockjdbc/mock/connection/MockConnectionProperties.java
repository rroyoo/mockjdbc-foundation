package io.github.rroyoo.mockjdbc.mock.connection;

public enum MockConnectionProperties {

    HOST("host"),
    IDLE_TIMEOUT("idleTimeout"),
    IDLE_TIMEOUT_TIME_UNIT("idleTimeoutTimeUnit"),
    KEEP_ALIVE_TIME("keepAliveTime"),
    KEEP_ALIVE_TIMEOUT("keepAliveTimeout"),
    KEEP_ALIVE_TIMEOUT_TIME_UNIT("keepAliveTimeoutTimeUnit"),
    KEEP_ALIVE_TIME_UNIT("keepAliveTimeUnit"),
    PORT("port");

    private final String key;

    MockConnectionProperties(String key) {
        this.key = key;
    }

    public String getKey() {
        return key;
    }
}
