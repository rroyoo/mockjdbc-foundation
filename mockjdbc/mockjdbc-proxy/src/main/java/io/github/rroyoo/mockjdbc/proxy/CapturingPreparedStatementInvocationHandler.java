package io.github.rroyoo.mockjdbc.proxy;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.TreeMap;

/**
 * JDK proxy {@link InvocationHandler} for {@link java.sql.PreparedStatement} and
 * {@link java.sql.CallableStatement} that captures SQL, bound parameters, execution results,
 * and ResultSet rows.
 *
 * <p><strong>Parameter tracking:</strong>
 * All {@code setXxx(int parameterIndex, T value)} calls populate an ordered {@link TreeMap}.
 * On every no-arg execute method the map is converted to a sorted {@link List} and cleared.
 * For {@code addBatch()}, the current parameter snapshot is saved to a batch group list before
 * clearing, enabling multi-group capture for batched executions.
 *
 * <p><strong>Intercepted execute methods (no-arg variants for PreparedStatement):</strong>
 * <ul>
 *   <li>{@code executeQuery()} — wraps returned ResultSet</li>
 *   <li>{@code executeUpdate()}, {@code executeLargeUpdate()} — captures update count</li>
 *   <li>{@code execute()} — wraps ResultSet or captures update count</li>
 *   <li>{@code executeBatch()}, {@code executeLargeBatch()} — captures per-batch update counts</li>
 * </ul>
 *
 * <p>Also handles CallableStatement: {@code registerOutParameter} and all name/index-based
 * getters are simply delegated — they do not affect capture semantics.
 */
final class CapturingPreparedStatementInvocationHandler implements InvocationHandler {

    private final PreparedStatement delegate;
    private final String sql;
    private final JdbcExecutionCapture capture;

    /** Current single-execution parameter set. Keyed by 1-based index. */
    private final TreeMap<Integer, Object> currentParams = new TreeMap<>();

    /** Batch groups accumulated by {@code addBatch()}. */
    private final List<List<Object>> batchGroups = new ArrayList<>();

    /**
     * Stores the wrapped ResultSet after {@code execute()} returns {@code true}.
     * Consumed and cleared on the next {@code getResultSet()} call.
     */
    private ResultSet pendingResultSet;

    CapturingPreparedStatementInvocationHandler(PreparedStatement delegate,
                                                String sql,
                                                JdbcExecutionCapture capture) {
        this.delegate = delegate;
        this.sql = sql;
        this.capture = capture;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        var name = method.getName();

        if ("equals".equals(name))   return proxy == (args != null ? args[0] : null);
        if ("hashCode".equals(name)) return System.identityHashCode(proxy);
        if ("toString".equals(name)) return "CapturingPreparedStatement{sql=" + sql + "}";

        // ── Parameter setters ────────────────────────────────────────────────
        if (name.startsWith("set") && args != null && args.length >= 1
                && args[0] instanceof Integer idx) {
            if ("setNull".equals(name)) {
                currentParams.put(idx, null);
            } else if (args.length >= 2) {
                currentParams.put(idx, args[1]);
            }
            return delegateInvoke(method, args);
        }

        // ── clearParameters() ────────────────────────────────────────────────
        if ("clearParameters".equals(name)) {
            currentParams.clear();
            return delegateInvoke(method, args);
        }

        // ── addBatch() (no-arg PreparedStatement variant) ────────────────────
        if ("addBatch".equals(name) && (args == null || args.length == 0)) {
            batchGroups.add(Collections.unmodifiableList(new ArrayList<>(sortedParamValues())));
            currentParams.clear();
            return delegateInvoke(method, args);
        }

        // ── executeQuery() ───────────────────────────────────────────────────
        if ("executeQuery".equals(name) && (args == null || args.length == 0)) {
            return captureExecuteQuery();
        }

        // ── executeUpdate() ──────────────────────────────────────────────────
        if ("executeUpdate".equals(name) && (args == null || args.length == 0)) {
            return captureExecuteUpdate();
        }

        // ── executeLargeUpdate() ─────────────────────────────────────────────
        if ("executeLargeUpdate".equals(name) && (args == null || args.length == 0)) {
            return captureExecuteLargeUpdate();
        }

        // ── execute() ────────────────────────────────────────────────────────
        if ("execute".equals(name) && (args == null || args.length == 0)) {
            return captureExecute();
        }

        // ── executeBatch() ───────────────────────────────────────────────────
        if ("executeBatch".equals(name)) {
            return captureExecuteBatch();
        }

        // ── executeLargeBatch() ──────────────────────────────────────────────
        if ("executeLargeBatch".equals(name)) {
            return captureExecuteLargeBatch();
        }

        // ── getResultSet() — return pending wrapped RS if present ────────────
        if ("getResultSet".equals(name) && pendingResultSet != null) {
            var rs = pendingResultSet;
            pendingResultSet = null;
            return rs;
        }

        // ── delegate everything else ─────────────────────────────────────────
        return delegateInvoke(method, args);
    }

    // ── Capture helpers ──────────────────────────────────────────────────────

    private ResultSet captureExecuteQuery() throws Throwable {
        var params = snapshot();
        var startMs = System.currentTimeMillis();
        ResultSet rawRs;
        try {
            rawRs = delegate.executeQuery();
        } catch (Exception e) {
            capture.captureUpdate(sql, params, 0L,
                    System.currentTimeMillis() - startMs, false, e);
            throw e;
        } finally {
            currentParams.clear();
        }
        return capture.wrapResultSet(sql, params, rawRs, startMs);
    }

    private int captureExecuteUpdate() throws Throwable {
        var params = snapshot();
        var startMs = System.currentTimeMillis();
        try {
            int count = delegate.executeUpdate();
            capture.captureUpdate(sql, params, count,
                    System.currentTimeMillis() - startMs, true, null);
            return count;
        } catch (Exception e) {
            capture.captureUpdate(sql, params, 0L,
                    System.currentTimeMillis() - startMs, false, e);
            throw e;
        } finally {
            currentParams.clear();
        }
    }

    private long captureExecuteLargeUpdate() throws Throwable {
        var params = snapshot();
        var startMs = System.currentTimeMillis();
        try {
            long count = delegate.executeLargeUpdate();
            capture.captureUpdate(sql, params, count,
                    System.currentTimeMillis() - startMs, true, null);
            return count;
        } catch (Exception e) {
            capture.captureUpdate(sql, params, 0L,
                    System.currentTimeMillis() - startMs, false, e);
            throw e;
        } finally {
            currentParams.clear();
        }
    }

    private boolean captureExecute() throws Throwable {
        var params = snapshot();
        var startMs = System.currentTimeMillis();
        boolean hasResultSet;
        try {
            hasResultSet = delegate.execute();
        } catch (Exception e) {
            capture.captureUpdate(sql, params, 0L,
                    System.currentTimeMillis() - startMs, false, e);
            currentParams.clear();
            throw e;
        }

        var elapsedMs = System.currentTimeMillis() - startMs;
        currentParams.clear();

        if (hasResultSet) {
            var rawRs = delegate.getResultSet();
            if (rawRs != null) {
                pendingResultSet = capture.wrapResultSet(sql, params, rawRs, startMs);
            }
        } else {
            capture.captureUpdate(sql, params, (long) delegate.getUpdateCount(),
                    elapsedMs, true, null);
        }
        return hasResultSet;
    }

    private int[] captureExecuteBatch() throws Throwable {
        var startMs = System.currentTimeMillis();
        try {
            int[] counts = delegate.executeBatch();
            captureBatchResult(counts);
            return counts;
        } catch (Exception e) {
            capture.captureUpdate(sql, List.of(), 0L,
                    System.currentTimeMillis() - startMs, false, e);
            throw e;
        } finally {
            batchGroups.clear();
            currentParams.clear();
        }
    }

    private long[] captureExecuteLargeBatch() throws Throwable {
        var startMs = System.currentTimeMillis();
        try {
            long[] counts = delegate.executeLargeBatch();
            captureLargeBatchResult(counts);
            return counts;
        } catch (Exception e) {
            capture.captureUpdate(sql, List.of(), 0L,
                    System.currentTimeMillis() - startMs, false, e);
            throw e;
        } finally {
            batchGroups.clear();
            currentParams.clear();
        }
    }

    private void captureBatchResult(int[] counts) {
        long total = 0;
        for (int c : counts) {
            if (c >= 0) total += c;
        }
        capture.captureUpdate(sql, List.of(), total, 0L, true, null);
    }

    private void captureLargeBatchResult(long[] counts) {
        long total = 0;
        for (long c : counts) {
            if (c >= 0) total += c;
        }
        capture.captureUpdate(sql, List.of(), total, 0L, true, null);
    }

    /** Returns the current parameters as an ordered list (sorted by index). */
    private List<Object> sortedParamValues() {
        return new ArrayList<>(currentParams.values());
    }

    /** Snapshot for use in a capture call (immutable). */
    private List<Object> snapshot() {
        if (currentParams.isEmpty()) return List.of();
        return Collections.unmodifiableList(new ArrayList<>(currentParams.values()));
    }

    private Object delegateInvoke(Method method, Object[] args) throws Throwable {
        try {
            return method.invoke(delegate, args);
        } catch (InvocationTargetException e) {
            throw e.getTargetException();
        }
    }
}
