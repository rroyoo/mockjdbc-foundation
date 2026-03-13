package io.github.rroyoo.mockjdbc.mock.connection;

import net.bytebuddy.implementation.bind.annotation.Origin;
import net.bytebuddy.implementation.bind.annotation.RuntimeType;

import java.lang.reflect.Method;
import java.sql.SQLException;

final class GenericVoidMethodHandler {

    @RuntimeType
    public static void handle(@Origin Method method) throws SQLException {

    }
}
