package io.github.rroyoo.mockjdbc.mock.connection;

import java.sql.SQLException;
import net.bytebuddy.implementation.bind.annotation.RuntimeType;

final class NullResultHandler {

    @RuntimeType
    public static Object handle() throws SQLException {
        return null;
    }
}
