package io.github.rroyoo.mockjdbc.mock.connection;

import io.github.rroyoo.mockjdbc.mock.driver.MockConfig;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.implementation.MethodCall;
import net.bytebuddy.implementation.MethodDelegation;

import java.sql.Connection;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesNoArguments;

final class ConnectionFactory {

    private ConnectionFactory() {}

    public static Connection create(MockConfig mockConfig) {

        if (mockConfig == null) {
            throw new IllegalArgumentException("MockConfig cannot be null");
        }

        try {
            var stateHandler = new ConnectionStateHandler();
            var closeMethod = ConnectionStateHandler.class.getMethod("close");
            var isClosedMethod = ConnectionStateHandler.class.getMethod("isClosed");

            var connectionBuilder = new ByteBuddy()
                    .subclass(Connection.class)
                    .method(named("commit").and(takesNoArguments()))
                    .intercept(MethodDelegation.to(GenericVoidMethodHandler.class))
                    .method(named("rollback").and(takesNoArguments()))
                    .intercept(MethodDelegation.to(GenericVoidMethodHandler.class))
                    .method(named("close").and(takesNoArguments()))
                    .intercept(MethodCall.invoke(closeMethod).on(stateHandler))
                    .method(named("isClosed").and(takesNoArguments()))
                    .intercept(MethodCall.invoke(isClosedMethod).on(stateHandler));

            try (var unloaded = connectionBuilder.make()) {
                return unloaded.load(ConnectionFactory.class.getClassLoader())
                        .getLoaded()
                        .getDeclaredConstructor()
                        .newInstance();
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
