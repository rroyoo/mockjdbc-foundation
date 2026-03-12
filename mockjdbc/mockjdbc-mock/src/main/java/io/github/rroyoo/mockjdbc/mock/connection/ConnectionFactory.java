package io.github.rroyoo.mockjdbc.mock.connection;

import io.github.rroyoo.mockjdbc.mock.driver.MockConfig;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.implementation.MethodCall;
import net.bytebuddy.implementation.MethodDelegation;

import java.sql.Connection;
import java.util.concurrent.Executor;

import java.sql.Savepoint;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;
import static net.bytebuddy.matcher.ElementMatchers.takesNoArguments;

final class ConnectionFactory {

    private ConnectionFactory() {}

    public static Connection create(MockConfig mockConfig) {

        if (mockConfig == null) {
            throw new IllegalArgumentException("MockConfig cannot be null");
        }

        try {
            var stateHandler = new ConnectionStateHandler();

            var connectionBuilder = new ByteBuddy()
                    .subclass(Connection.class)
                    .method(named("commit").and(takesNoArguments()))
                    .intercept(MethodDelegation.to(GenericVoidMethodHandler.class))
                    .method(named("rollback").and(takesNoArguments()))
                    .intercept(MethodDelegation.to(GenericVoidMethodHandler.class))
                    .method(named("rollback").and(takesArguments(Savepoint.class)))
                    .intercept(MethodDelegation.to(GenericVoidMethodHandler.class))
                    .method(named("releaseSavepoint").and(takesArguments(Savepoint.class)))
                    .intercept(MethodDelegation.to(GenericVoidMethodHandler.class))
                    .method(named("setSavepoint").and(takesNoArguments()))
                    .intercept(MethodDelegation.to(NullResultHandler.class))
                    .method(named("setSavepoint").and(takesArguments(String.class)))
                    .intercept(MethodDelegation.to(NullResultHandler.class))
                    .method(named("close").and(takesNoArguments()))
                    .intercept(MethodCall.invoke(ConnectionStateHandler.class.getMethod("close")).on(stateHandler))
                    .method(named("isClosed").and(takesNoArguments()))
                    .intercept(MethodCall.invoke(ConnectionStateHandler.class.getMethod("isClosed")).on(stateHandler))
                    .method(named("setAutoCommit").and(takesArguments(boolean.class)))
                    .intercept(MethodCall.invoke(ConnectionStateHandler.class.getMethod("setAutoCommit", boolean.class)).on(stateHandler).withAllArguments())
                    .method(named("getAutoCommit").and(takesNoArguments()))
                    .intercept(MethodCall.invoke(ConnectionStateHandler.class.getMethod("getAutoCommit")).on(stateHandler))
                    .method(named("setReadOnly").and(takesArguments(boolean.class)))
                    .intercept(MethodCall.invoke(ConnectionStateHandler.class.getMethod("setReadOnly", boolean.class)).on(stateHandler).withAllArguments())
                    .method(named("isReadOnly").and(takesNoArguments()))
                    .intercept(MethodCall.invoke(ConnectionStateHandler.class.getMethod("isReadOnly")).on(stateHandler))
                    .method(named("setTransactionIsolation").and(takesArguments(int.class)))
                    .intercept(MethodCall.invoke(ConnectionStateHandler.class.getMethod("setTransactionIsolation", int.class)).on(stateHandler).withAllArguments())
                    .method(named("getTransactionIsolation").and(takesNoArguments()))
                    .intercept(MethodCall.invoke(ConnectionStateHandler.class.getMethod("getTransactionIsolation")).on(stateHandler))
                    .method(named("setHoldability").and(takesArguments(int.class)))
                    .intercept(MethodCall.invoke(ConnectionStateHandler.class.getMethod("setHoldability", int.class)).on(stateHandler).withAllArguments())
                    .method(named("getHoldability").and(takesNoArguments()))
                    .intercept(MethodCall.invoke(ConnectionStateHandler.class.getMethod("getHoldability")).on(stateHandler))
                    .method(named("setCatalog").and(takesArguments(String.class)))
                    .intercept(MethodCall.invoke(ConnectionStateHandler.class.getMethod("setCatalog", String.class)).on(stateHandler).withAllArguments())
                    .method(named("getCatalog").and(takesNoArguments()))
                    .intercept(MethodCall.invoke(ConnectionStateHandler.class.getMethod("getCatalog")).on(stateHandler))
                    .method(named("setSchema").and(takesArguments(String.class)))
                    .intercept(MethodCall.invoke(ConnectionStateHandler.class.getMethod("setSchema", String.class)).on(stateHandler).withAllArguments())
                    .method(named("getSchema").and(takesNoArguments()))
                    .intercept(MethodCall.invoke(ConnectionStateHandler.class.getMethod("getSchema")).on(stateHandler))
                    .method(named("setNetworkTimeout").and(takesArguments(Executor.class, int.class)))
                    .intercept(MethodCall.invoke(ConnectionStateHandler.class.getMethod("setNetworkTimeout", Executor.class, int.class)).on(stateHandler).withAllArguments())
                    .method(named("getNetworkTimeout").and(takesNoArguments()))
                    .intercept(MethodCall.invoke(ConnectionStateHandler.class.getMethod("getNetworkTimeout")).on(stateHandler));

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
