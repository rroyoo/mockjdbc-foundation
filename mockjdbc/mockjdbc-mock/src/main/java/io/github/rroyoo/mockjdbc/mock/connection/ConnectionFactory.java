package io.github.rroyoo.mockjdbc.mock.connection;

import io.github.rroyoo.mockjdbc.mock.driver.MockConfig;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.implementation.MethodCall;
import net.bytebuddy.implementation.MethodDelegation;

import java.sql.Connection;
import java.sql.Savepoint;
import java.util.Properties;
import java.util.concurrent.Executor;

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
            var lifecycle    = new LifecycleHandler();
            var transaction  = new TransactionHandler();
            var config       = new ConfigHandler();
            var warnings     = new WarningsHandler();
            var clientInfo   = new ClientInfoHandler();

            var connectionBuilder = new ByteBuddy()
                    .subclass(Connection.class)
                    // --- lifecycle ---
                    .method(named("close").and(takesNoArguments()))
                    .intercept(MethodCall.invoke(LifecycleHandler.class.getMethod("close")).on(lifecycle))
                    .method(named("isClosed").and(takesNoArguments()))
                    .intercept(MethodCall.invoke(LifecycleHandler.class.getMethod("isClosed")).on(lifecycle))
                    // --- transaction ---
                    .method(named("commit").and(takesNoArguments()))
                    .intercept(MethodDelegation.to(GenericVoidMethodHandler.class))
                    .method(named("rollback").and(takesNoArguments()))
                    .intercept(MethodDelegation.to(GenericVoidMethodHandler.class))
                    .method(named("rollback").and(takesArguments(Savepoint.class)))
                    .intercept(MethodDelegation.to(GenericVoidMethodHandler.class))
                    .method(named("setAutoCommit").and(takesArguments(boolean.class)))
                    .intercept(MethodCall.invoke(TransactionHandler.class.getMethod("setAutoCommit", boolean.class)).on(transaction).withAllArguments())
                    .method(named("getAutoCommit").and(takesNoArguments()))
                    .intercept(MethodCall.invoke(TransactionHandler.class.getMethod("getAutoCommit")).on(transaction))
                    // --- savepoint ---
                    .method(named("releaseSavepoint").and(takesArguments(Savepoint.class)))
                    .intercept(MethodDelegation.to(GenericVoidMethodHandler.class))
                    .method(named("setSavepoint").and(takesNoArguments()))
                    .intercept(MethodDelegation.to(NullResultHandler.class))
                    .method(named("setSavepoint").and(takesArguments(String.class)))
                    .intercept(MethodDelegation.to(NullResultHandler.class))
                    // --- config ---
                    .method(named("setReadOnly").and(takesArguments(boolean.class)))
                    .intercept(MethodCall.invoke(ConfigHandler.class.getMethod("setReadOnly", boolean.class)).on(config).withAllArguments())
                    .method(named("isReadOnly").and(takesNoArguments()))
                    .intercept(MethodCall.invoke(ConfigHandler.class.getMethod("isReadOnly")).on(config))
                    .method(named("setTransactionIsolation").and(takesArguments(int.class)))
                    .intercept(MethodCall.invoke(ConfigHandler.class.getMethod("setTransactionIsolation", int.class)).on(config).withAllArguments())
                    .method(named("getTransactionIsolation").and(takesNoArguments()))
                    .intercept(MethodCall.invoke(ConfigHandler.class.getMethod("getTransactionIsolation")).on(config))
                    .method(named("setHoldability").and(takesArguments(int.class)))
                    .intercept(MethodCall.invoke(ConfigHandler.class.getMethod("setHoldability", int.class)).on(config).withAllArguments())
                    .method(named("getHoldability").and(takesNoArguments()))
                    .intercept(MethodCall.invoke(ConfigHandler.class.getMethod("getHoldability")).on(config))
                    .method(named("setCatalog").and(takesArguments(String.class)))
                    .intercept(MethodCall.invoke(ConfigHandler.class.getMethod("setCatalog", String.class)).on(config).withAllArguments())
                    .method(named("getCatalog").and(takesNoArguments()))
                    .intercept(MethodCall.invoke(ConfigHandler.class.getMethod("getCatalog")).on(config))
                    .method(named("setSchema").and(takesArguments(String.class)))
                    .intercept(MethodCall.invoke(ConfigHandler.class.getMethod("setSchema", String.class)).on(config).withAllArguments())
                    .method(named("getSchema").and(takesNoArguments()))
                    .intercept(MethodCall.invoke(ConfigHandler.class.getMethod("getSchema")).on(config))
                    .method(named("setNetworkTimeout").and(takesArguments(Executor.class, int.class)))
                    .intercept(MethodCall.invoke(ConfigHandler.class.getMethod("setNetworkTimeout", Executor.class, int.class)).on(config).withAllArguments())
                    .method(named("getNetworkTimeout").and(takesNoArguments()))
                    .intercept(MethodCall.invoke(ConfigHandler.class.getMethod("getNetworkTimeout")).on(config))
                    // --- warnings ---
                    .method(named("getWarnings").and(takesNoArguments()))
                    .intercept(MethodCall.invoke(WarningsHandler.class.getMethod("getWarnings")).on(warnings))
                    .method(named("clearWarnings").and(takesNoArguments()))
                    .intercept(MethodCall.invoke(WarningsHandler.class.getMethod("clearWarnings")).on(warnings))
                    // --- client info ---
                    .method(named("setClientInfo").and(takesArguments(String.class, String.class)))
                    .intercept(MethodCall.invoke(ClientInfoHandler.class.getMethod("setClientInfo", String.class, String.class)).on(clientInfo).withAllArguments())
                    .method(named("setClientInfo").and(takesArguments(Properties.class)))
                    .intercept(MethodCall.invoke(ClientInfoHandler.class.getMethod("setClientInfo", Properties.class)).on(clientInfo).withAllArguments())
                    .method(named("getClientInfo").and(takesNoArguments()))
                    .intercept(MethodCall.invoke(ClientInfoHandler.class.getMethod("getClientInfo")).on(clientInfo))
                    .method(named("getClientInfo").and(takesArguments(String.class)))
                    .intercept(MethodCall.invoke(ClientInfoHandler.class.getMethod("getClientInfo", String.class)).on(clientInfo).withAllArguments());

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
