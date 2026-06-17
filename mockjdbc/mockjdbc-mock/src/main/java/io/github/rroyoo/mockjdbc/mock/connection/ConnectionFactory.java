package io.github.rroyoo.mockjdbc.mock.connection;

import io.github.rroyoo.mockjdbc.mock.driver.MockConfig;
import io.github.rroyoo.mockjdbc.mock.statement.StatementFactory;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.Savepoint;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.Executor;

/**
 * Creates mock {@link Connection} instances backed by JDK dynamic proxies.
 *
 * <p>JDK proxy classes are cached by the runtime for a given (ClassLoader, interfaces[]) tuple,
 * so no bytecode is generated after the first call. Only the lightweight {@link InvocationHandler}
 * is allocated per connection, eliminating the ByteBuddy class-generation overhead.
 */
public final class ConnectionFactory {

    private ConnectionFactory() {}

    public static Connection create(MockConfig mockConfig) {
        if (mockConfig == null) {
            throw new IllegalArgumentException("MockConfig cannot be null");
        }
        try {
            var lifecycle   = new LifecycleHandler();
            var transaction = new TransactionHandler();
            var config      = new ConfigHandler();
            var warnings    = new WarningsHandler();
            var clientInfo  = new ClientInfoHandler();
            var statements  = new StatementFactory(mockConfig);

            return (Connection) Proxy.newProxyInstance(
                    ConnectionFactory.class.getClassLoader(),
                    new Class<?>[]{ Connection.class },
                    new ConnectionInvocationHandler(lifecycle, transaction, config, warnings, clientInfo, statements)
            );
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // ── Dispatch ─────────────────────────────────────────────────────────────

    @FunctionalInterface
    interface MethodInvoker {
        Object invoke(Object[] args) throws Exception;
    }

    private static final class ConnectionInvocationHandler implements InvocationHandler {

        private final Map<Method, MethodInvoker> dispatch;

        ConnectionInvocationHandler(
                LifecycleHandler lifecycle,
                TransactionHandler transaction,
                ConfigHandler config,
                WarningsHandler warnings,
                ClientInfoHandler clientInfo,
                StatementFactory statements) throws NoSuchMethodException {
            this.dispatch = buildDispatch(lifecycle, transaction, config, warnings, clientInfo, statements);
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            var name = method.getName();
            if ("equals".equals(name))   return proxy == (args != null ? args[0] : null);
            if ("hashCode".equals(name)) return System.identityHashCode(proxy);
            if ("toString".equals(name)) return "MockConnection";

            var invoker = dispatch.get(method);
            if (invoker != null) {
                return invoker.invoke(args);
            }

            // Safe defaults for any unregistered method.
            var returnType = method.getReturnType();
            if (returnType == void.class || returnType == Void.class) return null;
            if (returnType == boolean.class) return false;
            if (returnType == int.class)     return 0;
            if (returnType == long.class)    return 0L;
            return null;
        }

        private static Map<Method, MethodInvoker> buildDispatch(
                LifecycleHandler lifecycle,
                TransactionHandler transaction,
                ConfigHandler config,
                WarningsHandler warnings,
                ClientInfoHandler clientInfo,
                StatementFactory statements) throws NoSuchMethodException {

            var d = new HashMap<Method, MethodInvoker>();
            var c = Connection.class;

            // ── lifecycle ────────────────────────────────────────────────────
            d.put(c.getMethod("close"),    args -> { lifecycle.close(); return null; });
            d.put(c.getMethod("isClosed"), args -> lifecycle.isClosed());

            // ── transaction ──────────────────────────────────────────────────
            d.put(c.getMethod("commit"),                        args -> null);  // no-op
            d.put(c.getMethod("rollback"),                      args -> null);  // no-op
            d.put(c.getMethod("rollback", Savepoint.class),     args -> null);  // no-op
            d.put(c.getMethod("setAutoCommit", boolean.class),  args -> { transaction.setAutoCommit((boolean) args[0]); return null; });
            d.put(c.getMethod("getAutoCommit"),                 args -> transaction.getAutoCommit());

            // ── savepoint ────────────────────────────────────────────────────
            d.put(c.getMethod("releaseSavepoint", Savepoint.class), args -> null);
            d.put(c.getMethod("setSavepoint"),                      args -> null);
            d.put(c.getMethod("setSavepoint", String.class),        args -> null);

            // ── config ───────────────────────────────────────────────────────
            d.put(c.getMethod("setReadOnly", boolean.class),                   args -> { config.setReadOnly((boolean) args[0]); return null; });
            d.put(c.getMethod("isReadOnly"),                                   args -> config.isReadOnly());
            d.put(c.getMethod("setTransactionIsolation", int.class),           args -> { config.setTransactionIsolation((int) args[0]); return null; });
            d.put(c.getMethod("getTransactionIsolation"),                      args -> config.getTransactionIsolation());
            d.put(c.getMethod("setHoldability", int.class),                    args -> { config.setHoldability((int) args[0]); return null; });
            d.put(c.getMethod("getHoldability"),                               args -> config.getHoldability());
            d.put(c.getMethod("setCatalog", String.class),                     args -> { config.setCatalog((String) args[0]); return null; });
            d.put(c.getMethod("getCatalog"),                                   args -> config.getCatalog());
            d.put(c.getMethod("setSchema", String.class),                      args -> { config.setSchema((String) args[0]); return null; });
            d.put(c.getMethod("getSchema"),                                    args -> config.getSchema());
            d.put(c.getMethod("setNetworkTimeout", Executor.class, int.class), args -> { config.setNetworkTimeout((Executor) args[0], (int) args[1]); return null; });
            d.put(c.getMethod("getNetworkTimeout"),                            args -> config.getNetworkTimeout());

            // ── warnings ─────────────────────────────────────────────────────
            d.put(c.getMethod("getWarnings"),   args -> warnings.getWarnings());
            d.put(c.getMethod("clearWarnings"), args -> { warnings.clearWarnings(); return null; });

            // ── client info ──────────────────────────────────────────────────
            d.put(c.getMethod("setClientInfo", String.class, String.class), args -> { clientInfo.setClientInfo((String) args[0], (String) args[1]); return null; });
            d.put(c.getMethod("setClientInfo", Properties.class),           args -> { clientInfo.setClientInfo((Properties) args[0]); return null; });
            d.put(c.getMethod("getClientInfo"),                             args -> clientInfo.getClientInfo());
            d.put(c.getMethod("getClientInfo", String.class),               args -> clientInfo.getClientInfo((String) args[0]));

            // ── native SQL ───────────────────────────────────────────────────
            d.put(c.getMethod("nativeSQL", String.class), args -> (String) args[0]);

            // ── statement factory ────────────────────────────────────────────
            d.put(c.getMethod("createStatement"),                                            args -> statements.createStatement());
            d.put(c.getMethod("createStatement", int.class, int.class),                     args -> statements.createStatement((int) args[0], (int) args[1]));
            d.put(c.getMethod("createStatement", int.class, int.class, int.class),          args -> statements.createStatement((int) args[0], (int) args[1], (int) args[2]));
            d.put(c.getMethod("prepareStatement", String.class),                             args -> statements.prepareStatement((String) args[0]));
            d.put(c.getMethod("prepareStatement", String.class, int.class),                 args -> statements.prepareStatement((String) args[0], (int) args[1]));
            d.put(c.getMethod("prepareStatement", String.class, int.class, int.class),      args -> statements.prepareStatement((String) args[0], (int) args[1], (int) args[2]));
            d.put(c.getMethod("prepareStatement", String.class, int.class, int.class, int.class), args -> statements.prepareStatement((String) args[0], (int) args[1], (int) args[2], (int) args[3]));
            d.put(c.getMethod("prepareStatement", String.class, int[].class),               args -> statements.prepareStatement((String) args[0], (int[]) args[1]));
            d.put(c.getMethod("prepareStatement", String.class, String[].class),            args -> statements.prepareStatement((String) args[0], (String[]) args[1]));
            d.put(c.getMethod("prepareCall", String.class),                                  args -> statements.prepareCall((String) args[0]));
            d.put(c.getMethod("prepareCall", String.class, int.class, int.class),           args -> statements.prepareCall((String) args[0], (int) args[1], (int) args[2]));
            d.put(c.getMethod("prepareCall", String.class, int.class, int.class, int.class), args -> statements.prepareCall((String) args[0], (int) args[1], (int) args[2], (int) args[3]));

            // ── misc ─────────────────────────────────────────────────────────
            d.put(c.getMethod("getMetaData"),                       args -> (DatabaseMetaData) null);
            d.put(c.getMethod("getTypeMap"),                        args -> Map.of());
            d.put(c.getMethod("setTypeMap", Map.class),             args -> null);
            d.put(c.getMethod("isWrapperFor", Class.class),         args -> false);
            d.put(c.getMethod("unwrap", Class.class),               args -> { throw new java.sql.SQLException("Not a wrapper for " + args[0]); });

            return d;
        }
    }
}
