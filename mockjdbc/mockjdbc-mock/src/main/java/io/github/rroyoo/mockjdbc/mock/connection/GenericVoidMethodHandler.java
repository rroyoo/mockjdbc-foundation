package io.github.rroyoo.mockjdbc.mock.connection;

import java.lang.reflect.Method;
import java.sql.SQLException;
import net.bytebuddy.implementation.bind.annotation.Origin;
import net.bytebuddy.implementation.bind.annotation.RuntimeType;

final class GenericVoidMethodHandler {

    @RuntimeType
    public static void handle(@Origin Method method) throws SQLException {}
}
