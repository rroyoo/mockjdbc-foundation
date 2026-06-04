package io.github.rroyoo.mockjdbc.mock.connection;

import net.bytebuddy.implementation.bind.annotation.Argument;

import java.sql.SQLException;

final class NativeSqlHandler {

    public String nativeSQL(@Argument(0) String sql) throws SQLException {
        return sql;
    }
}

