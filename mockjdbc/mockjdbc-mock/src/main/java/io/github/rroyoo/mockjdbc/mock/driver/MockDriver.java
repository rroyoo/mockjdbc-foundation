package io.github.rroyoo.mockjdbc.mock.driver;

import io.github.rroyoo.mockjdbc.mock.connection.ConnectionFactory;

import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverPropertyInfo;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.Properties;
import java.util.logging.Logger;

public final class MockDriver implements Driver {

    @Override
    public Connection connect(String url, Properties info) throws SQLException {
        if (!acceptsURL(url)) {
            return null;
        }

        try {
            var mockConfig = URLParser.parse(url, info);
            return ConnectionFactory.create(mockConfig);
        } catch (RuntimeException e) {
            throw new SQLException("Error creating mock connection", e);
        } catch (Exception e) {
            throw new SQLException("Invalid mock JDBC URL: " + url, e);
        }
    }

    @Override
    public boolean acceptsURL(String url) {
        if (url == null) {
            return false;
        }

        try {
            URLParser.parse(url);
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    @Override
    public DriverPropertyInfo[] getPropertyInfo(String url, Properties info) {
        return new DriverPropertyInfo[0];
    }

    @Override
    public int getMajorVersion() {
        return 1;
    }

    @Override
    public int getMinorVersion() {
        return 0;
    }

    @Override
    public boolean jdbcCompliant() {
        return false;
    }

    @Override
    public Logger getParentLogger() throws SQLFeatureNotSupportedException {
        return Logger.getGlobal();
    }
}
