package io.github.rroyoo.mockjdbc.proxy;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.sql.ParameterMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * JDK proxy {@link InvocationHandler} for {@link java.sql.PreparedStatement} and {@link
 * java.sql.CallableStatement} that captures SQL, bound parameters, execution results, and ResultSet
 * rows.
 *
 * <p><strong>Parameter tracking:</strong> All {@code setXxx(int parameterIndex, T value)} calls
 * populate an ordered {@link TreeMap}. Bound values persist across executions and {@code
 * addBatch()} (standard PreparedStatement semantics) until {@code clearParameters()} or a setter
 * replaces them. For {@code addBatch()}, the current parameter snapshot is saved to a batch group
 * list, enabling multi-group capture for batched executions.
 *
 * <p><strong>Intercepted execute methods (no-arg variants for PreparedStatement):</strong>
 *
 * <ul>
 *   <li>{@code executeQuery()} — wraps returned ResultSet
 *   <li>{@code executeUpdate()}, {@code executeLargeUpdate()} — captures update count
 *   <li>{@code execute()} — wraps ResultSet or captures update count
 *   <li>{@code executeBatch()}, {@code executeLargeBatch()} — captures per-batch update counts
 * </ul>
 *
 * <p><strong>CallableStatement OUT/INOUT parameter capture (index-based only):</strong> {@code
 * registerOutParameter(int, ...)} records the declared parameter mode (OUT, or INOUT when an IN
 * value was already bound via {@code setXxx}). Because OUT values are only known to the caller
 * after retrieving them via {@code getXxx(int)} — which happens strictly after {@code
 * execute()}/{@code executeUpdate()} returns — the capture event for a callable execution that
 * declares OUT parameters is <em>deferred</em>: it is held as a {@link PendingCallableCapture} and
 * flushed (with whatever OUT values were retrieved by then) on the next lifecycle event (a
 * subsequent {@code set*}/{@code execute*}/{@code registerOutParameter} call, or {@code close()}).
 * Named-parameter registration ({@code registerOutParameter(String, ...)}) and the
 * ResultSet-returning branch of {@code execute()} are not covered by deferred OUT capture; those
 * paths fall back to the existing immediate-capture behavior without merged OUT values.
 */
final class CapturingPreparedStatementInvocationHandler implements InvocationHandler {

    private static final Object UNHANDLED = new Object();

    private final PreparedStatement delegate;
    private final String sql;
    private final JdbcExecutionCapture capture;
    private final boolean returnGeneratedKeys;

    /** Current single-execution parameter set. Keyed by 1-based index. */
    private final TreeMap<Integer, Object> currentParams = new TreeMap<>();

    /** Batch groups accumulated by {@code addBatch()}. */
    private final List<List<Object>> batchGroups = new ArrayList<>();

    /** Declared OUT/INOUT parameter modes by 1-based index, from {@code registerOutParameter}. */
    private final TreeMap<Integer, Integer> outParamModesByIndex = new TreeMap<>();

    /**
     * Values retrieved via {@code getXxx(int)} for registered OUT/INOUT indices, since the last
     * flush.
     */
    private final TreeMap<Integer, Object> outValuesByIndex = new TreeMap<>();

    /** Deferred capture awaiting OUT parameter retrieval before being published. */
    private PendingCallableCapture pendingCallableCapture;

    private PendingGeneratedKeysCapture pendingGeneratedKeysCapture;
    private ResultSet activeGeneratedKeys;

    /**
     * Stores the wrapped ResultSet after {@code execute()} returns {@code true}. Consumed and
     * cleared on the next {@code getResultSet()} call.
     */
    private ResultSet pendingResultSet;

    /** Snapshot of a callable execution awaiting OUT parameter values before being published. */
    private record PendingCallableCapture(
            SortedMap<Integer, Object> inParamsByIndex,
            long updateCount,
            long elapsedMs,
            boolean success,
            Throwable error) {}

    private record PendingGeneratedKeysCapture(
            List<Object> parameters, long updateCount, long elapsedMs) {}

    CapturingPreparedStatementInvocationHandler(
            PreparedStatement delegate, String sql, JdbcExecutionCapture capture) {
        this(delegate, sql, capture, false);
    }

    CapturingPreparedStatementInvocationHandler(
            PreparedStatement delegate,
            String sql,
            JdbcExecutionCapture capture,
            boolean returnGeneratedKeys) {
        this.delegate = delegate;
        this.sql = sql;
        this.capture = capture;
        this.returnGeneratedKeys = returnGeneratedKeys;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        var name = method.getName();

        if ("equals".equals(name)) return proxy == (args != null ? args[0] : null);
        if ("hashCode".equals(name)) return System.identityHashCode(proxy);
        if ("toString".equals(name)) return "CapturingPreparedStatement{sql=" + sql + "}";

        return invokeJdbcMethod(name, method, args);
    }

    private Object invokeJdbcMethod(String name, Method method, Object[] args) throws Throwable {
        var result = handleLifecycleMethod(name, method, args);
        if (result != UNHANDLED) {
            return result;
        }

        result = handleCallableMethod(name, method, args);
        if (result != UNHANDLED) {
            return result;
        }

        result = handleParameterMethod(name, method, args);
        if (result != UNHANDLED) {
            return result;
        }

        result = handleExecutionMethod(name, method, args);
        if (result != UNHANDLED) {
            return result;
        }

        return delegateInvoke(method, args);
    }

    private Object handleLifecycleMethod(String name, Method method, Object[] args)
            throws Throwable {
        if ("close".equals(name)) {
            flushPendingCallableCapture();
            flushPendingGeneratedKeysCapture();
            return delegateInvoke(method, args);
        }

        if ("getGeneratedKeys".equals(name) && activeGeneratedKeys != null) {
            return activeGeneratedKeys;
        }

        if ("getGeneratedKeys".equals(name) && pendingGeneratedKeysCapture != null) {
            return captureGeneratedKeys();
        }

        if (name.startsWith("execute")
                && (pendingGeneratedKeysCapture != null || activeGeneratedKeys != null)) {
            flushPendingGeneratedKeysCapture();
        }
        return UNHANDLED;
    }

    private Object handleCallableMethod(String name, Method method, Object[] args)
            throws Throwable {
        var result = handleOutParameterGetter(name, method, args);
        if (result != UNHANDLED) {
            return result;
        }

        if (pendingCallableCapture != null) {
            flushPendingCallableCapture();
        }

        return handleRegisterOutParameter(name, method, args);
    }

    private Object handleOutParameterGetter(String name, Method method, Object[] args)
            throws Throwable {
        if (isRegisteredOutGetter(name, args) && pendingCallableCapture != null) {
            var idx = (Integer) args[0];
            var result = delegateInvoke(method, args);
            outValuesByIndex.put(idx, result);
            return result;
        }
        return UNHANDLED;
    }

    private boolean isRegisteredOutGetter(String name, Object[] args) {
        return name.startsWith("get")
                && args != null
                && args.length >= 1
                && args[0] instanceof Integer idx
                && outParamModesByIndex.containsKey(idx);
    }

    private Object handleRegisterOutParameter(String name, Method method, Object[] args)
            throws Throwable {
        if ("registerOutParameter".equals(name)
                && args != null
                && args.length >= 2
                && args[0] instanceof Integer idx) {
            var mode =
                    currentParams.containsKey(idx)
                            ? ParameterMetaData.parameterModeInOut
                            : ParameterMetaData.parameterModeOut;
            outParamModesByIndex.put(idx, mode);
            return delegateInvoke(method, args);
        }
        return UNHANDLED;
    }

    private Object handleParameterMethod(String name, Method method, Object[] args)
            throws Throwable {
        var result = handleParameterSetter(name, method, args);
        if (result != UNHANDLED) {
            return result;
        }
        return handleParameterLifecycleMethod(name, method, args);
    }

    private Object handleParameterSetter(String name, Method method, Object[] args)
            throws Throwable {
        if (name.startsWith("set")
                && args != null
                && args.length >= 1
                && args[0] instanceof Integer idx) {
            if ("setNull".equals(name)) {
                currentParams.put(idx, null);
            } else if (args.length >= 2) {
                currentParams.put(idx, args[1]);
            }
            // registerOutParameter may have come first: binding a value upgrades OUT to INOUT
            outParamModesByIndex.computeIfPresent(
                    idx, (i, mode) -> ParameterMetaData.parameterModeInOut);
            return delegateInvoke(method, args);
        }
        return UNHANDLED;
    }

    private Object handleParameterLifecycleMethod(String name, Method method, Object[] args)
            throws Throwable {
        if ("clearParameters".equals(name)) {
            currentParams.clear();
            return delegateInvoke(method, args);
        }

        if ("addBatch".equals(name) && hasNoArguments(args)) {
            batchGroups.add(Collections.unmodifiableList(new ArrayList<>(sortedParamValues())));
            return delegateInvoke(method, args);
        }
        return UNHANDLED;
    }

    private Object handleExecutionMethod(String name, Method method, Object[] args)
            throws Throwable {
        var result = handleBatchExecution(name);
        if (result != UNHANDLED) {
            return result;
        }

        result = handleSingleExecution(name, args);
        if (result != UNHANDLED) {
            return result;
        }

        if ("getResultSet".equals(name) && pendingResultSet != null) {
            var rs = pendingResultSet;
            pendingResultSet = null;
            return rs;
        }
        return UNHANDLED;
    }

    private Object handleBatchExecution(String name) throws Throwable {
        if ("executeBatch".equals(name)) {
            return captureExecuteBatch();
        }
        if ("executeLargeBatch".equals(name)) {
            return captureExecuteLargeBatch();
        }
        return UNHANDLED;
    }

    private Object handleSingleExecution(String name, Object[] args) throws Throwable {
        if ("executeQuery".equals(name) && hasNoArguments(args)) {
            return captureExecuteQuery();
        }
        if ("executeUpdate".equals(name) && hasNoArguments(args)) {
            return captureExecuteUpdate();
        }
        if ("executeLargeUpdate".equals(name) && hasNoArguments(args)) {
            return captureExecuteLargeUpdate();
        }
        if ("execute".equals(name) && hasNoArguments(args)) {
            return captureExecute();
        }
        return UNHANDLED;
    }

    private static boolean hasNoArguments(Object[] args) {
        return args == null || args.length == 0;
    }

    // ── Capture helpers ──────────────────────────────────────────────────────

    private ResultSet captureExecuteQuery() throws Throwable {
        var params = snapshot();
        var startMs = System.currentTimeMillis();
        ResultSet rawRs;
        try {
            rawRs = delegate.executeQuery();
        } catch (Exception e) {
            capture.captureUpdate(sql, params, 0L, System.currentTimeMillis() - startMs, false, e);
            throw e;
        }
        return capture.wrapResultSet(sql, params, rawRs, startMs);
    }

    private int captureExecuteUpdate() throws Throwable {
        var hasOutParams = !outParamModesByIndex.isEmpty();
        var inParamsByIndex = new TreeMap<>(currentParams);
        var params = snapshot();
        var startMs = System.currentTimeMillis();
        try {
            int count = delegate.executeUpdate();
            var elapsedMs = System.currentTimeMillis() - startMs;
            if (hasOutParams) {
                pendingCallableCapture =
                        new PendingCallableCapture(inParamsByIndex, count, elapsedMs, true, null);
            } else if (returnGeneratedKeys) {
                pendingGeneratedKeysCapture =
                        new PendingGeneratedKeysCapture(params, count, elapsedMs);
            } else {
                capture.captureUpdate(sql, params, count, elapsedMs, true, null);
            }
            return count;
        } catch (Exception e) {
            var elapsedMs = System.currentTimeMillis() - startMs;
            if (hasOutParams) {
                pendingCallableCapture =
                        new PendingCallableCapture(inParamsByIndex, 0L, elapsedMs, false, e);
            } else {
                capture.captureUpdate(sql, params, 0L, elapsedMs, false, e);
            }
            throw e;
        }
    }

    private long captureExecuteLargeUpdate() throws Throwable {
        var params = snapshot();
        var startMs = System.currentTimeMillis();
        try {
            long count = delegate.executeLargeUpdate();
            var elapsedMs = System.currentTimeMillis() - startMs;
            if (returnGeneratedKeys) {
                pendingGeneratedKeysCapture =
                        new PendingGeneratedKeysCapture(params, count, elapsedMs);
            } else {
                capture.captureUpdate(sql, params, count, elapsedMs, true, null);
            }
            return count;
        } catch (Exception e) {
            capture.captureUpdate(sql, params, 0L, System.currentTimeMillis() - startMs, false, e);
            throw e;
        }
    }

    private boolean captureExecute() throws Throwable {
        var hasOutParams = !outParamModesByIndex.isEmpty();
        var inParamsByIndex = new TreeMap<>(currentParams);
        var params = snapshot();
        var startMs = System.currentTimeMillis();
        boolean hasResultSet;
        try {
            hasResultSet = delegate.execute();
        } catch (Exception e) {
            var elapsedMs = System.currentTimeMillis() - startMs;
            if (hasOutParams) {
                pendingCallableCapture =
                        new PendingCallableCapture(inParamsByIndex, 0L, elapsedMs, false, e);
            } else {
                capture.captureUpdate(sql, params, 0L, elapsedMs, false, e);
            }
            throw e;
        }

        var elapsedMs = System.currentTimeMillis() - startMs;

        if (hasResultSet) {
            var rawRs = delegate.getResultSet();
            if (rawRs != null) {
                pendingResultSet = capture.wrapResultSet(sql, params, rawRs, startMs);
            }
        } else if (hasOutParams) {
            pendingCallableCapture =
                    new PendingCallableCapture(
                            inParamsByIndex,
                            (long) delegate.getUpdateCount(),
                            elapsedMs,
                            true,
                            null);
        } else {
            var updateCount = (long) delegate.getUpdateCount();
            if (returnGeneratedKeys) {
                pendingGeneratedKeysCapture =
                        new PendingGeneratedKeysCapture(params, updateCount, elapsedMs);
            } else {
                capture.captureUpdate(sql, params, updateCount, elapsedMs, true, null);
            }
        }
        return hasResultSet;
    }

    private int[] captureExecuteBatch() throws Throwable {
        var groups = List.copyOf(batchGroups);
        var startMs = System.currentTimeMillis();
        try {
            int[] counts = delegate.executeBatch();
            var longCounts = new long[counts.length];
            for (var i = 0; i < counts.length; i++) longCounts[i] = counts[i];
            capture.captureBatch(
                    sql, groups, longCounts, System.currentTimeMillis() - startMs, true, null);
            return counts;
        } catch (Exception e) {
            capture.captureBatch(sql, groups, null, System.currentTimeMillis() - startMs, false, e);
            throw e;
        } finally {
            batchGroups.clear();
        }
    }

    private long[] captureExecuteLargeBatch() throws Throwable {
        var groups = List.copyOf(batchGroups);
        var startMs = System.currentTimeMillis();
        try {
            long[] counts = delegate.executeLargeBatch();
            capture.captureBatch(
                    sql, groups, counts, System.currentTimeMillis() - startMs, true, null);
            return counts;
        } catch (Exception e) {
            capture.captureBatch(sql, groups, null, System.currentTimeMillis() - startMs, false, e);
            throw e;
        } finally {
            batchGroups.clear();
        }
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

    // ── Deferred CallableStatement OUT/INOUT parameter capture ───────────────

    /**
     * Publishes a deferred callable capture, merging any OUT/INOUT values retrieved via {@code
     * getXxx(int)} since the execution completed. Values not yet retrieved fall back to the bound
     * IN value (or {@code null} for pure OUT parameters never read back).
     */
    private void flushPendingCallableCapture() {
        if (pendingCallableCapture == null) {
            return;
        }
        var pending = pendingCallableCapture;
        pendingCallableCapture = null;
        var merged = mergeCallableParameters(pending.inParamsByIndex());
        outValuesByIndex.clear();

        if (pending.error() != null) {
            capture.captureCallableUpdate(
                    sql, merged, 0L, pending.elapsedMs(), false, pending.error());
        } else {
            capture.captureCallableUpdate(
                    sql, merged, pending.updateCount(), pending.elapsedMs(), true, null);
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
                    sql,
                    pending.parameters(),
                    pending.updateCount(),
                    pending.elapsedMs(),
                    true,
                    null);
            throw error;
        }
        try {
            activeGeneratedKeys =
                    capture.captureUpdateWithGeneratedKeys(
                            sql,
                            pending.parameters(),
                            pending.updateCount(),
                            pending.elapsedMs(),
                            true,
                            null,
                            generatedKeys);
            return activeGeneratedKeys;
        } catch (java.sql.SQLException error) {
            capture.captureUpdate(
                    sql,
                    pending.parameters(),
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
                sql, pending.parameters(), pending.updateCount(), pending.elapsedMs(), true, null);
    }

    /**
     * Merges bound IN values, declared OUT/INOUT modes, and retrieved OUT values into an ordered
     * parameter list keyed by 1-based JDBC index.
     */
    private List<JdbcExecutionCapture.CapturedParameter> mergeCallableParameters(
            SortedMap<Integer, Object> inParamsByIndex) {
        var indices = new TreeSet<Integer>();
        indices.addAll(inParamsByIndex.keySet());
        indices.addAll(outParamModesByIndex.keySet());

        var result = new ArrayList<JdbcExecutionCapture.CapturedParameter>(indices.size());
        for (var idx : indices) {
            var mode = outParamModesByIndex.getOrDefault(idx, ParameterMetaData.parameterModeIn);
            var value =
                    outValuesByIndex.containsKey(idx)
                            ? outValuesByIndex.get(idx)
                            : inParamsByIndex.get(idx);
            result.add(new JdbcExecutionCapture.CapturedParameter(idx, mode, value));
        }
        return result;
    }

    private Object delegateInvoke(Method method, Object[] args) throws Throwable {
        try {
            return method.invoke(delegate, args);
        } catch (InvocationTargetException e) {
            throw e.getTargetException();
        }
    }
}
