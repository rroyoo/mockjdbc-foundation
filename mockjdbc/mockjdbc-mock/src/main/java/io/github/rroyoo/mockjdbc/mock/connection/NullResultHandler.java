package io.github.rroyoo.mockjdbc.mock.connection;

import net.bytebuddy.implementation.bind.annotation.RuntimeType;

import java.sql.SQLException;

final class NullResultHandler {

    @RuntimeType
    public static Object handle() throws SQLException {
        return null;
    }
}
