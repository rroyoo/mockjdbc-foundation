package io.github.rroyoo.mockjdbc.proxy;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;

/**
 * JDK proxy {@link InvocationHandler} for plain {@link Statement} that captures SQL executions
 * and their results as {@link JdbcQueryInterceptedEvent} and protobuf
 * {@link io.github.rroyoo.mockjdbc.mock.MockedQuery} events.
 *
 * <p>Intercepted methods (SQL always provided as first {@code String} argument):
 * <ul>
 *   <li>{@code executeQuery(String)} — wraps the returned ResultSet for row capture</li>
 *   <li>{@code executeUpdate(String, ...)}, {@code executeLargeUpdate(String, ...)} — captures update count</li>
 *   <li>{@code execute(String, ...)} — wraps ResultSet when result is a query; captures update count otherwise</li>
 * </ul>
 * All other Statement methods are forwarded to the delegate.
 */
final class CapturingStatementInvocationHandler implements InvocationHandler {

    private final Statement delegate;
    private final JdbcExecutionCapture capture;

    /**
     * Stores the wrapped ResultSet after {@code execute()} returns {@code true}. Cleared on next
     * {@code getResultSet()} call so the caller receives it exactly once, matching JDBC semantics.
     */
    private ResultSet pendingResultSet;

    CapturingStatementInvocationHandler(Statement delegate, JdbcExecutionCapture capture) {
        this.delegate = delegate;
        this.capture = capture;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        var name = method.getName();

        if ("equals".equals(name))   return proxy == (args != null ? args[0] : null);
        if ("hashCode".equals(name)) return System.identityHashCode(proxy);
        if ("toString".equals(name)) return "CapturingStatement{" + delegate + "}";

        // ── executeQuery(String) ─────────────────────────────────────────────
        if ("executeQuery".equals(name) && args != null && args.length == 1 && args[0] instanceof String sql) {
            return captureExecuteQuery(sql);
        }

        // ── executeUpdate(String, ...) ───────────────────────────────────────
        if ("executeUpdate".equals(name) && args != null && args.length >= 1 && args[0] instanceof String sql) {
            return captureExecuteUpdate(method, sql, args);
        }

        // ── executeLargeUpdate(String, ...) ──────────────────────────────────
        if ("executeLargeUpdate".equals(name) && args != null && args.length >= 1 && args[0] instanceof String sql) {
            return captureExecuteLargeUpdate(method, sql, args);
        }

        // ── execute(String, ...) ─────────────────────────────────────────────
        if ("execute".equals(name) && args != null && args.length >= 1 && args[0] instanceof String sql) {
            return captureExecute(method, sql, args);
        }

        // ── getResultSet() — return pending wrapped RS if present ────────────
        if ("getResultSet".equals(name) && pendingResultSet != null) {
            var rs = pendingResultSet;
            pendingResultSet = null;
            return rs;
        }

        // ── delegate everything else ─────────────────────────────────────────
        try {
            return method.invoke(delegate, args);
        } catch (InvocationTargetException e) {
            throw e.getTargetException();
        }
    }

    // ── Capture helpers ──────────────────────────────────────────────────────

    private ResultSet captureExecuteQuery(String sql) throws Throwable {
        var startMs = System.currentTimeMillis();
        ResultSet rawRs;
        try {
            rawRs = delegate.executeQuery(sql);
        } catch (Exception e) {
            capture.captureUpdate(sql, List.of(), 0L,
                    System.currentTimeMillis() - startMs, false, e);
            throw e;
        }
        return capture.wrapResultSet(sql, List.of(), rawRs, startMs);
    }

    private int captureExecuteUpdate(Method method, String sql, Object[] args) throws Throwable {
        var startMs = System.currentTimeMillis();
        try {
            int count = (int) method.invoke(delegate, args);
            capture.captureUpdate(sql, List.of(), count,
                    System.currentTimeMillis() - startMs, true, null);
            return count;
        } catch (InvocationTargetException e) {
            capture.captureUpdate(sql, List.of(), 0L,
                    System.currentTimeMillis() - startMs, false, e.getTargetException());
            throw e.getTargetException();
        }
    }

    private long captureExecuteLargeUpdate(Method method, String sql, Object[] args) throws Throwable {
        var startMs = System.currentTimeMillis();
        try {
            long count = (long) method.invoke(delegate, args);
            capture.captureUpdate(sql, List.of(), count,
                    System.currentTimeMillis() - startMs, true, null);
            return count;
        } catch (InvocationTargetException e) {
            capture.captureUpdate(sql, List.of(), 0L,
                    System.currentTimeMillis() - startMs, false, e.getTargetException());
            throw e.getTargetException();
        }
    }

    private boolean captureExecute(Method method, String sql, Object[] args) throws Throwable {
        var startMs = System.currentTimeMillis();
        boolean hasResultSet;
        try {
            hasResultSet = (boolean) method.invoke(delegate, args);
        } catch (InvocationTargetException e) {
            capture.captureUpdate(sql, List.of(), 0L,
                    System.currentTimeMillis() - startMs, false, e.getTargetException());
            throw e.getTargetException();
        }

        var elapsedMs = System.currentTimeMillis() - startMs;

        if (hasResultSet) {
            var rawRs = delegate.getResultSet();
            if (rawRs != null) {
                pendingResultSet = capture.wrapResultSet(sql, List.of(), rawRs, startMs);
            }
        } else {
            capture.captureUpdate(sql, List.of(), (long) delegate.getUpdateCount(),
                    elapsedMs, true, null);
        }
        return hasResultSet;
    }
}
