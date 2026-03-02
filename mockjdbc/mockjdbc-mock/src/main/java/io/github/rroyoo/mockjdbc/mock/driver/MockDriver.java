package io.github.rroyoo.mockjdbc.mock.driver;

import io.github.rroyoo.mockjdbc.mock.connection.ConnectionPool;
import io.github.rroyoo.mockjdbc.mock.connection.MockConnectionPool;

import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverPropertyInfo;
import java.sql.SQLException;
import java.util.Properties;
import java.util.logging.Logger;

public final class MockDriver implements Driver {

    private static final int MAJOR_VERSION = 1;
    private static final int MINOR_VERSION = 0;
    private static final boolean JDBC_COMPLIANT = false;

    static {
        try {
            java.sql.DriverManager.registerDriver(new MockDriver());
        } catch (SQLException e) {
            throw new RuntimeException("Failed to register MockDriver", e);
        }
    }

    private final ConnectionPool connectionPool;
    private final UrlDriverParser urlDriverParser;

    public MockDriver() {
        connectionPool = new MockConnectionPool();
        urlDriverParser = new UrlMockDriverParser();
    }

    public MockDriver(ConnectionPool connectionPool, UrlDriverParser urlDriverParser) {
        this.urlDriverParser = urlDriverParser;
        this.connectionPool = connectionPool;
    }

    @Override
    public Connection connect(String url, Properties info) throws SQLException {
        if(!acceptsURL(url)) {
            throw new SQLException("Could not connect to the database. URL not accepted: " + url);
        }

        return connectionPool.getConnection(url, urlDriverParser.getProperties(url));
    }

    @Override
    public boolean acceptsURL(String url) {
        return urlDriverParser.accepts(url);
    }

    @Override
    public DriverPropertyInfo[] getPropertyInfo(String url, Properties info) {
        var props = urlDriverParser.getProperties(url);

        if (props == null) {
            return new DriverPropertyInfo[0];
        }

        return props.entrySet().stream().map(entry ->
                new DriverPropertyInfo(entry.getKey().toString(), entry.getValue().toString()))
                .toArray(DriverPropertyInfo[]::new);
    }

    @Override
    public int getMajorVersion() {
        return MAJOR_VERSION;
    }

    @Override
    public int getMinorVersion() {
        return MINOR_VERSION;
    }

    @Override
    public boolean jdbcCompliant() {
        return JDBC_COMPLIANT;
    }

    @Override
    public Logger getParentLogger() {
        return Logger.getLogger(Logger.GLOBAL_LOGGER_NAME);
    }
}
