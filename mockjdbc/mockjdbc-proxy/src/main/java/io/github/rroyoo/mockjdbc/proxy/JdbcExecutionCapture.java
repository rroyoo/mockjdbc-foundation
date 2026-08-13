package io.github.rroyoo.mockjdbc.proxy;

import io.github.rroyoo.mockjdbc.mock.CallableStatement;
import io.github.rroyoo.mockjdbc.mock.MockedQuery;
import io.github.rroyoo.mockjdbc.mock.ParameterMetadata;
import io.github.rroyoo.mockjdbc.mock.PlainStatement;
import io.github.rroyoo.mockjdbc.mock.PreparedStatement;
import io.github.rroyoo.mockjdbc.mock.QueryExecutionStatus;
import io.github.rroyoo.mockjdbc.mock.SerializedResultSet;

import java.sql.ParameterMetaData;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;

/**
 * Core capture component: stores intercepted JDBC query events and emits protobuf events via
 * {@link AsyncMockedQueryEventDispatcher}.
 *
 * <p>All capture calls are driven by the agent-level JDBC wrapper handlers.
 */
public final class JdbcExecutionCapture implements AutoCloseable {

    private final String datasourceId;
    private final ConcurrentLinkedQueue<JdbcQueryInterceptedEvent> events = new ConcurrentLinkedQueue<>();
    private final Consumer<JdbcQueryInterceptedEvent> localConsumer;
    final AsyncMockedQueryEventDispatcher dispatcher;
    final ByteBuddyResultSetWrapperFactory resultSetWrapperFactory;

    public JdbcExecutionCapture(String datasourceId,
                                Consumer<JdbcQueryInterceptedEvent> localConsumer,
                                MockedQueryEventProducer producer,
                                AsyncDispatchConfig config) {
        this.datasourceId = normalizeDatasourceId(datasourceId);
        this.localConsumer = Objects.requireNonNull(localConsumer, "localConsumer is required");
        Objects.requireNonNull(producer, "producer is required");
        Objects.requireNonNull(config, "config is required");
        this.dispatcher = new AsyncMockedQueryEventDispatcher(producer, config);
        this.resultSetWrapperFactory = new ByteBuddyResultSetWrapperFactory(config.rowBatchSize());
    }

    /**
     * Captures a non-SELECT execution (INSERT, UPDATE, DELETE, DDL, error path).
     *
     * @param sql         executed SQL string
     * @param params      ordered parameter values for the single execution (empty for plain Statement)
     * @param updateCount rows affected (0 for error path)
     * @param elapsedMs   elapsed execution time in milliseconds
     * @param success     whether the execution completed without error
     * @param error       non-null when the execution threw an exception
     */
    void captureUpdate(String sql,
                       List<Object> params,
                       long updateCount,
                       long elapsedMs,
                       boolean success,
                       Throwable error) {
        // List.copyOf rejects null elements; use ArrayList wrapper to allow null parameters (e.g. setNull())
        var paramGroups = params.isEmpty()
                ? List.<List<Object>>of()
                : List.of(Collections.unmodifiableList(new ArrayList<>(params)));
        var event = new JdbcQueryInterceptedEvent(datasourceId, sql, paramGroups, elapsedMs, success, error);
        events.add(event);
        localConsumer.accept(event);

        var paramMetadata = toParameterMetadata(params);
        var serializedResultSet = ByteBuddyResultSetWrapperFactory.resultSetFromUpdateResult(updateCount);
        dispatcher.publish(buildMockedQuery(sql, paramMetadata, serializedResultSet,
                error, elapsedMs, success, datasourceId, updateCount, 0L));
    }

    /**
     * Captures a SELECT result after the ResultSet has been fully consumed.
     * Called from inside the {@link ByteBuddyResultSetWrapperFactory} row-capturing wrapper.
     */
    void captureSelect(String sql,
                       List<Object> params,
                       SerializedResultSet serializedResultSet,
                       long elapsedMs,
                       boolean success) {
        var paramMetadata = toParameterMetadata(params);
        dispatcher.publish(buildMockedQuery(sql, paramMetadata, serializedResultSet,
                null, elapsedMs, success, datasourceId, 0L, serializedResultSet.getRowsCount()));
    }

    /**
     * Wraps a raw ResultSet with capturing logic. The {@link SerializedResultSet} is emitted to
     * {@link #captureSelect} when the caller iterates or closes the ResultSet.
     *
     * @param sql     original SQL for the SELECT
     * @param params  bound parameters
     * @param rawRs   real ResultSet from the JDBC driver
     * @param startMs wall-clock time when the statement execution began
     * @return        a capturing ResultSet proxy
     * @throws java.sql.SQLException if the wrapper proxy cannot be created
     */
    java.sql.ResultSet wrapResultSet(String sql,
                                     List<Object> params,
                                     java.sql.ResultSet rawRs,
                                     long startMs) throws java.sql.SQLException {
        return resultSetWrapperFactory.wrap(rawRs,
                serialized -> captureSelect(sql, params, serialized,
                        System.currentTimeMillis() - startMs, true));
    }

    public List<JdbcQueryInterceptedEvent> events() {
        return List.copyOf(events);
    }

    public void clear() {
        events.clear();
    }

    String datasourceId() {
        return datasourceId;
    }

    @Override
    public void close() {
        dispatcher.close();
    }

    // ── Proto event building ────────────────────────────────────────────────────

    static MockedQuery buildMockedQuery(String sql,
                                        List<ParameterMetadata> parameters,
                                        SerializedResultSet serializedResultSet,
                                        Throwable error,
                                        long elapsedTimeMillis,
                                        boolean success,
                                        String datasourceId,
                                        long updateCount,
                                        long rowCount) {
        var builder = MockedQuery.newBuilder()
                .setResultSet(serializedResultSet)
                .setDatasourceId(datasourceId)
                .setElapsedTimeMillis(elapsedTimeMillis)
                .setStatus(success
                        ? QueryExecutionStatus.QUERY_EXECUTION_STATUS_SUCCESS
                        : QueryExecutionStatus.QUERY_EXECUTION_STATUS_ERROR)
                .setRowCount(rowCount)
                .setUpdateCount(updateCount)
                .setEventId(UUID.randomUUID().toString())
                .setObservedAt(nowTimestamp());

        var normalizedSql = sql == null ? "" : sql.trim();

        if (error != null) {
            builder.setError(io.github.rroyoo.mockjdbc.mock.QueryError.newBuilder()
                    .setType(error.getClass().getName())
                    .setMessage(error.getMessage() == null ? "" : error.getMessage())
                    .build());
        }

        if (normalizedSql.startsWith("{") || normalizedSql.toLowerCase().startsWith("call")) {
            builder.setCallableStatement(CallableStatement.newBuilder()
                    .setCallSql(sql == null ? "" : sql)
                    .addAllParameters(parameters)
                    .build());
        } else if (parameters.isEmpty()) {
            builder.setSimpleStatement(PlainStatement.newBuilder().setSql(sql == null ? "" : sql).build());
        } else {
            builder.setPreparedStatement(PreparedStatement.newBuilder()
                    .setSql(sql == null ? "" : sql)
                    .addAllParameters(parameters)
                    .build());
        }

        return builder.build();
    }

    static List<ParameterMetadata> toParameterMetadata(List<Object> params) {
        if (params == null || params.isEmpty()) {
            return List.of();
        }
        List<ParameterMetadata> result = new ArrayList<>(params.size());
        for (var index = 0; index < params.size(); index++) {
            var value = params.get(index);
            result.add(ParameterMetadata.newBuilder()
                    .setIndex(index + 1)
                    .setMode(ParameterMetaData.parameterModeIn)
                    .setSqlType(inferSqlType(value))
                    .setTypeName(value == null ? "NULL" : value.getClass().getSimpleName())
                    .setValue(ByteBuddyResultSetWrapperFactory.toJdbcValue(value))
                    .build());
        }
        return Collections.unmodifiableList(result);
    }

    static int inferSqlType(Object value) {
        if (value == null)                                                             return Types.NULL;
        if (value instanceof String)                                                   return Types.VARCHAR;
        if (value instanceof Integer || value instanceof Long
                || value instanceof Short || value instanceof Byte)                    return Types.BIGINT;
        if (value instanceof Boolean)                                                  return Types.BOOLEAN;
        if (value instanceof Float || value instanceof Double)                         return Types.DOUBLE;
        if (value instanceof byte[])                                                   return Types.BINARY;
        return Types.JAVA_OBJECT;
    }

    private static com.google.protobuf.Timestamp nowTimestamp() {
        var millis = System.currentTimeMillis();
        var seconds = millis / 1000;
        var nanos = (int) ((millis % 1000) * 1_000_000);
        return com.google.protobuf.Timestamp.newBuilder().setSeconds(seconds).setNanos(nanos).build();
    }

    private static String normalizeDatasourceId(String datasourceId) {
        if (datasourceId == null) {
            return "default-datasource";
        }
        var trimmed = datasourceId.trim();
        return trimmed.isEmpty() ? "default-datasource" : trimmed;
    }
}
