package io.github.rroyoo.mockjdbc.proxy;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;

/**
 * JDK proxy {@link InvocationHandler} for {@link Connection} that wraps every statement it
 * creates with a capturing proxy.
 *
 * <p>Intercepts:
 * <ul>
 *   <li>{@code createStatement(...)} — returns a {@link CapturingStatementInvocationHandler} proxy</li>
 *   <li>{@code prepareStatement(sql, ...)} — returns a {@link CapturingPreparedStatementInvocationHandler} proxy</li>
 *   <li>{@code prepareCall(sql, ...)} — returns a {@link CapturingPreparedStatementInvocationHandler} proxy (CallableStatement)</li>
 * </ul>
 * All other Connection methods are forwarded to the delegate.
 */
final class CapturingConnectionInvocationHandler implements InvocationHandler {

    private final Connection delegate;
    private final JdbcExecutionCapture capture;

    private CapturingConnectionInvocationHandler(Connection delegate, JdbcExecutionCapture capture) {
        this.delegate = delegate;
        this.capture = capture;
    }

    /**
     * Wraps the given Connection. If proxy creation fails, the original Connection is returned
     * unchanged.
     */
    static Connection wrap(Connection delegate, JdbcCaptureRegistration registration) {
        try {
            ClassLoader cl = resolveClassLoader(delegate);
            return (Connection) Proxy.newProxyInstance(
                    cl,
                    new Class<?>[] { Connection.class },
                    new CapturingConnectionInvocationHandler(delegate, registration.capture())
            );
        } catch (Exception ignored) {
            return delegate;
        }
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        var name = method.getName();

        if ("equals".equals(name))   return proxy == (args != null ? args[0] : null);
        if ("hashCode".equals(name)) return System.identityHashCode(proxy);
        if ("toString".equals(name)) return "CapturingConnection{" + delegate + "}";

        try {
            if ("createStatement".equals(name)) {
                var stmt = (Statement) method.invoke(delegate, args);
                return wrapStatement(stmt);
            }

            if ("prepareStatement".equals(name) && args != null && args.length >= 1
                    && args[0] instanceof String sql) {
                var stmt = (PreparedStatement) method.invoke(delegate, args);
                return wrapPreparedStatement(stmt, sql);
            }

            if ("prepareCall".equals(name) && args != null && args.length >= 1
                    && args[0] instanceof String sql) {
                var stmt = (CallableStatement) method.invoke(delegate, args);
                return wrapCallableStatement(stmt, sql);
            }

            return method.invoke(delegate, args);

        } catch (InvocationTargetException e) {
            throw e.getTargetException();
        }
    }

    private Statement wrapStatement(Statement delegate) {
        ClassLoader cl = resolveClassLoader(delegate);
        return (Statement) Proxy.newProxyInstance(
                cl,
                new Class<?>[] { Statement.class },
                new CapturingStatementInvocationHandler(delegate, capture)
        );
    }

    private PreparedStatement wrapPreparedStatement(PreparedStatement delegate, String sql) {
        ClassLoader cl = resolveClassLoader(delegate);
        return (PreparedStatement) Proxy.newProxyInstance(
                cl,
                new Class<?>[] { PreparedStatement.class },
                new CapturingPreparedStatementInvocationHandler(delegate, sql, capture)
        );
    }

    private CallableStatement wrapCallableStatement(CallableStatement delegate, String sql) {
        ClassLoader cl = resolveClassLoader(delegate);
        return (CallableStatement) Proxy.newProxyInstance(
                cl,
                new Class<?>[] { CallableStatement.class },
                new CapturingPreparedStatementInvocationHandler(delegate, sql, capture)
        );
    }

    private static ClassLoader resolveClassLoader(Object obj) {
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        if (cl == null && obj != null) {
            cl = obj.getClass().getClassLoader();
        }
        if (cl == null) {
            cl = ClassLoader.getSystemClassLoader();
        }
        return cl;
    }
}
