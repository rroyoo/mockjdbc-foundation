package io.github.rroyoo.mockjdbc.mock.connection;

import java.sql.SQLException;

final class NativeSqlHandler {

    /** The mock driver forwards SQL untouched, so the native form equals the input. */
    public String nativeSQL(String sql) throws SQLException {
        if (sql == null) {
            throw new SQLException("SQL must not be null");
        }
        return sql;
    }
}
