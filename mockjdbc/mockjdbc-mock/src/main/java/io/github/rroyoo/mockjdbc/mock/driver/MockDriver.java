package io.github.rroyoo.mockjdbc.mock.driver;

import io.github.rroyoo.mockjdbc.mock.connection.ConnectionPool;
import io.github.rroyoo.mockjdbc.mock.connection.MockConnectionPool;

import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverPropertyInfo;
import java.sql.SQLException;
import java.util.Properties;
import java.util.logging.Logger;

/**
 * Mock JDBC Driver implementation. Register via {@link #registerDriver()} if needed.
 * This driver connects to a mocked database using a connection pool.
 */
public final class MockDriver implements Driver {

    private record DriverVersion(int major, int minor, boolean jdbcCompliant) {}

    private static final DriverVersion VERSION = new DriverVersion(1, 0, false);

    private final ConnectionPool connectionPool;
    private final UrlDriverParser urlDriverParser;

    public MockDriver() {
        this(new MockConnectionPool(), new UrlMockDriverParser());
    }

    public MockDriver(ConnectionPool connectionPool, UrlDriverParser urlDriverParser) {
        this.connectionPool = connectionPool;
        this.urlDriverParser = urlDriverParser;
    }

    /**
     * Register this driver with the DriverManager.
     * Call this method once at application startup if using static registration.
     */
    public static void registerDriver() throws SQLException {
        java.sql.DriverManager.registerDriver(new MockDriver());
    }

    @Override
    public Connection connect(String url, Properties info) throws SQLException {
        if (!acceptsURL(url)) {
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

        return (props != null)
            ? props.entrySet().stream()
                .map(entry -> new DriverPropertyInfo(
                    entry.getKey().toString(),
                    entry.getValue().toString()
                ))
                .toArray(DriverPropertyInfo[]::new)
            : new DriverPropertyInfo[0];
    }

    @Override
    public int getMajorVersion() {
        return VERSION.major();
    }

    @Override
    public int getMinorVersion() {
        return VERSION.minor();
    }

    @Override
    public boolean jdbcCompliant() {
        return VERSION.jdbcCompliant();
    }

    @Override
    public Logger getParentLogger() {
        return Logger.getLogger(Logger.GLOBAL_LOGGER_NAME);
    }
}
