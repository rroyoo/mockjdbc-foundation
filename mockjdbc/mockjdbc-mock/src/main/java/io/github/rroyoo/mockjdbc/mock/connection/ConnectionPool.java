package io.github.rroyoo.mockjdbc.mock.connection;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Properties;

public interface ConnectionPool {

    Connection getConnection(String url, Properties properties) throws SQLException;
}
