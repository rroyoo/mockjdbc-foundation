package io.github.rroyoo.mockjdbc.proxy;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * JDK proxy {@link InvocationHandler} for plain {@link Statement} that captures SQL executions and
 * their results as {@link JdbcQueryInterceptedEvent} and protobuf {@link
 * io.github.rroyoo.mockjdbc.mock.MockedQuery} events.
 *
 * <p>Intercepted methods (SQL always provided as first {@code String} argument):
 *
 * <ul>
 *   <li>{@code executeQuery(String)} — wraps the returned ResultSet for row capture
 *   <li>{@code executeUpdate(String, ...)}, {@code executeLargeUpdate(String, ...)} — captures
 *       update count
 *   <li>{@code execute(String, ...)} — wraps ResultSet when result is a query; captures update
 *       count otherwise
 *   <li>{@code addBatch(String)}, {@code executeBatch()}, {@code executeLargeBatch()} — captures
 *       each command and its update count
 * </ul>
 *
 * All other Statement methods are forwarded to the delegate.
 */
final class CapturingStatementInvocationHandler implements InvocationHandler {

    private static final Object UNHANDLED = new Object();

    private final Statement delegate;
    private final JdbcExecutionCapture capture;
    private PendingGeneratedKeysCapture pendingGeneratedKeysCapture;
    private ResultSet activeGeneratedKeys;
    private final List<String> batchSql = new ArrayList<>();

    /**
     * Stores the wrapped ResultSet after {@code execute()} returns {@code true}. Cleared on next
     * {@code getResultSet()} call so the caller receives it exactly once, matching JDBC semantics.
     */
    private ResultSet pendingResultSet;

    private record PendingGeneratedKeysCapture(String sql, long updateCount, long elapsedMs) {}

    CapturingStatementInvocationHandler(Statement delegate, JdbcExecutionCapture capture) {
        this.delegate = delegate;
        this.capture = capture;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        var name = method.getName();

        if ("equals".equals(name)) return proxy == (args != null ? args[0] : null);
        if ("hashCode".equals(name)) return System.identityHashCode(proxy);
        if ("toString".equals(name)) return "CapturingStatement{" + delegate + "}";

        return invokeJdbcMethod(name, method, args);
    }

    private Object invokeJdbcMethod(String name, Method method, Object[] args) throws Throwable {
        var result = handleLifecycleMethod(name, method, args);
        if (result != UNHANDLED) {
            return result;
        }

        result = handleBatchMethod(name, method, args);
        if (result != UNHANDLED) {
            return result;
        }

        result = handleSqlExecutionMethod(name, method, args);
        if (result != UNHANDLED) {
            return result;
        }

        result = handlePendingResultSet(name);
        if (result != UNHANDLED) {
            return result;
        }
        return delegateInvoke(method, args);
    }

    private Object handleLifecycleMethod(String name, Method method, Object[] args)
            throws Throwable {
        if ("close".equals(name)) {
            flushPendingGeneratedKeysCapture();
            return delegateInvoke(method, args);
        }

        if (name.startsWith("execute")
                && (pendingGeneratedKeysCapture != null || activeGeneratedKeys != null)) {
            flushPendingGeneratedKeysCapture();
        }

        if ("getGeneratedKeys".equals(name) && activeGeneratedKeys != null) {
            return activeGeneratedKeys;
        }

        if ("getGeneratedKeys".equals(name) && pendingGeneratedKeysCapture != null) {
            return captureGeneratedKeys();
        }
        return UNHANDLED;
    }

    private Object handleBatchMethod(String name, Method method, Object[] args) throws Throwable {
        var result = handleBatchMutationMethod(name, method, args);
        if (result != UNHANDLED) {
            return result;
        }
        return handleBatchExecutionMethod(name, method, args);
    }

    private Object handleBatchMutationMethod(String name, Method method, Object[] args)
            throws Throwable {
        if ("addBatch".equals(name) && hasSingleSqlArgument(args)) {
            var result = delegateInvoke(method, args);
            batchSql.add((String) args[0]);
            return result;
        }

        if ("clearBatch".equals(name)) {
            var result = delegateInvoke(method, args);
            batchSql.clear();
            return result;
        }
        return UNHANDLED;
    }

    private Object handleBatchExecutionMethod(String name, Method method, Object[] args)
            throws Throwable {
        if ("executeBatch".equals(name) && hasNoArguments(args)) {
            return captureExecuteBatch(method);
        }

        if ("executeLargeBatch".equals(name) && hasNoArguments(args)) {
            return captureExecuteLargeBatch(method);
        }
        return UNHANDLED;
    }

    private Object handleSqlExecutionMethod(String name, Method method, Object[] args)
            throws Throwable {
        if ("executeQuery".equals(name) && hasSingleSqlArgument(args)) {
            return captureExecuteQuery((String) args[0]);
        }
        if ("executeUpdate".equals(name) && hasSqlArgument(args)) {
            return captureExecuteUpdate(method, (String) args[0], args);
        }
        if ("executeLargeUpdate".equals(name) && hasSqlArgument(args)) {
            return captureExecuteLargeUpdate(method, (String) args[0], args);
        }
        if ("execute".equals(name) && hasSqlArgument(args)) {
            return captureExecute(method, (String) args[0], args);
        }
        return UNHANDLED;
    }

    private Object handlePendingResultSet(String name) {
        if ("getResultSet".equals(name) && pendingResultSet != null) {
            var rs = pendingResultSet;
            pendingResultSet = null;
            return rs;
        }
        return UNHANDLED;
    }

    private static boolean hasSingleSqlArgument(Object[] args) {
        return args != null && args.length == 1 && args[0] instanceof String;
    }

    private static boolean hasSqlArgument(Object[] args) {
        return args != null && args.length >= 1 && args[0] instanceof String;
    }

    private static boolean hasNoArguments(Object[] args) {
        return args == null || args.length == 0;
    }

    // ── Capture helpers ──────────────────────────────────────────────────────

    private ResultSet captureExecuteQuery(String sql) throws Throwable {
        flushPendingGeneratedKeysCapture();
        var startMs = System.currentTimeMillis();
        ResultSet rawRs;
        try {
            rawRs = delegate.executeQuery(sql);
        } catch (Exception e) {
            capture.captureUpdate(
                    sql, List.of(), 0L, System.currentTimeMillis() - startMs, false, e);
            throw e;
        }
        return capture.wrapResultSet(sql, List.of(), rawRs, startMs);
    }

    private int[] captureExecuteBatch(Method method) throws Throwable {
        var statements = List.copyOf(batchSql);
        var startMs = System.currentTimeMillis();
        try {
            var counts = (int[]) method.invoke(delegate);
            var longCounts = new long[counts.length];
            for (var i = 0; i < counts.length; i++) {
                longCounts[i] = counts[i];
            }
            capture.captureStatementBatch(
                    statements, longCounts, System.currentTimeMillis() - startMs, true, null);
            return counts;
        } catch (InvocationTargetException e) {
            capture.captureStatementBatch(
                    statements,
                    null,
                    System.currentTimeMillis() - startMs,
                    false,
                    e.getTargetException());
            throw e.getTargetException();
        } finally {
            batchSql.clear();
        }
    }

    private long[] captureExecuteLargeBatch(Method method) throws Throwable {
        var statements = List.copyOf(batchSql);
        var startMs = System.currentTimeMillis();
        try {
            var counts = (long[]) method.invoke(delegate);
            capture.captureStatementBatch(
                    statements, counts, System.currentTimeMillis() - startMs, true, null);
            return counts;
        } catch (InvocationTargetException e) {
            capture.captureStatementBatch(
                    statements,
                    null,
                    System.currentTimeMillis() - startMs,
                    false,
                    e.getTargetException());
            throw e.getTargetException();
        } finally {
            batchSql.clear();
        }
    }

    private int captureExecuteUpdate(Method method, String sql, Object[] args) throws Throwable {
        flushPendingGeneratedKeysCapture();
        var startMs = System.currentTimeMillis();
        try {
            int count = (int) method.invoke(delegate, args);
            captureUpdate(
                    sql,
                    List.of(),
                    count,
                    System.currentTimeMillis() - startMs,
                    true,
                    null,
                    requestsGeneratedKeys(args));
            return count;
        } catch (InvocationTargetException e) {
            capture.captureUpdate(
                    sql,
                    List.of(),
                    0L,
                    System.currentTimeMillis() - startMs,
                    false,
                    e.getTargetException());
            throw e.getTargetException();
        }
    }

    private long captureExecuteLargeUpdate(Method method, String sql, Object[] args)
            throws Throwable {
        flushPendingGeneratedKeysCapture();
        var startMs = System.currentTimeMillis();
        try {
            long count = (long) method.invoke(delegate, args);
            captureUpdate(
                    sql,
                    List.of(),
                    count,
                    System.currentTimeMillis() - startMs,
                    true,
                    null,
                    requestsGeneratedKeys(args));
            return count;
        } catch (InvocationTargetException e) {
            capture.captureUpdate(
                    sql,
                    List.of(),
                    0L,
                    System.currentTimeMillis() - startMs,
                    false,
                    e.getTargetException());
            throw e.getTargetException();
        }
    }

    private boolean captureExecute(Method method, String sql, Object[] args) throws Throwable {
        flushPendingGeneratedKeysCapture();
        var startMs = System.currentTimeMillis();
        boolean hasResultSet;
        try {
            hasResultSet = (boolean) method.invoke(delegate, args);
        } catch (InvocationTargetException e) {
            capture.captureUpdate(
                    sql,
                    List.of(),
                    0L,
                    System.currentTimeMillis() - startMs,
                    false,
                    e.getTargetException());
            throw e.getTargetException();
        }

        var elapsedMs = System.currentTimeMillis() - startMs;

        if (hasResultSet) {
            var rawRs = delegate.getResultSet();
            if (rawRs != null) {
                pendingResultSet = capture.wrapResultSet(sql, List.of(), rawRs, startMs);
            }
        } else {
            captureUpdate(
                    sql,
                    List.of(),
                    (long) delegate.getUpdateCount(),
                    elapsedMs,
                    true,
                    null,
                    requestsGeneratedKeys(args));
        }
        return hasResultSet;
    }

    static boolean requestsGeneratedKeys(Object[] args) {
        if (args == null || args.length != 2) {
            return false;
        }
        if (args[1] instanceof Integer flag) {
            return flag == Statement.RETURN_GENERATED_KEYS;
        }
        if (args[1] instanceof int[] columnIndexes) {
            return columnIndexes.length > 0;
        }
        return args[1] instanceof String[] columnNames && columnNames.length > 0;
    }

    private void captureUpdate(
            String sql,
            List<Object> params,
            long updateCount,
            long elapsedMs,
            boolean success,
            Throwable error,
            boolean requestsGeneratedKeys)
            throws Throwable {
        if (requestsGeneratedKeys && success) {
            pendingGeneratedKeysCapture =
                    new PendingGeneratedKeysCapture(sql, updateCount, elapsedMs);
        } else {
            capture.captureUpdate(sql, params, updateCount, elapsedMs, success, error);
        }
    }

    private ResultSet captureGeneratedKeys() throws Throwable {
        var pending = pendingGeneratedKeysCapture;
        pendingGeneratedKeysCapture = null;
        ResultSet generatedKeys;
        try {
            generatedKeys = delegate.getGeneratedKeys();
        } catch (java.sql.SQLException error) {
            capture.captureUpdate(
                    pending.sql(),
                    List.of(),
                    pending.updateCount(),
                    pending.elapsedMs(),
                    true,
                    null);
            throw error;
        }
        try {
            activeGeneratedKeys =
                    capture.captureUpdateWithGeneratedKeys(
                            pending.sql(),
                            List.of(),
                            pending.updateCount(),
                            pending.elapsedMs(),
                            true,
                            null,
                            generatedKeys);
            return activeGeneratedKeys;
        } catch (java.sql.SQLException error) {
            capture.captureUpdate(
                    pending.sql(),
                    List.of(),
                    pending.updateCount(),
                    pending.elapsedMs(),
                    true,
                    null);
            throw error;
        }
    }

    private void flushPendingGeneratedKeysCapture() throws java.sql.SQLException {
        if (activeGeneratedKeys != null) {
            activeGeneratedKeys.close();
            activeGeneratedKeys = null;
        }
        if (pendingGeneratedKeysCapture == null) {
            return;
        }
        var pending = pendingGeneratedKeysCapture;
        pendingGeneratedKeysCapture = null;
        capture.captureUpdate(
                pending.sql(), List.of(), pending.updateCount(), pending.elapsedMs(), true, null);
    }

    private Object delegateInvoke(Method method, Object[] args) throws Throwable {
        try {
            return method.invoke(delegate, args);
        } catch (InvocationTargetException e) {
            throw e.getTargetException();
        }
    }
}
