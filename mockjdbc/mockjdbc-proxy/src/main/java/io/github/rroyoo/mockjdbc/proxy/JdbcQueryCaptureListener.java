package io.github.rroyoo.mockjdbc.proxy;

import io.github.rroyoo.mockjdbc.mock.CallableStatement;
import io.github.rroyoo.mockjdbc.mock.MockedQuery;
import io.github.rroyoo.mockjdbc.mock.ParameterMetadata;
import io.github.rroyoo.mockjdbc.mock.PlainStatement;
import io.github.rroyoo.mockjdbc.mock.PreparedStatement;
import io.github.rroyoo.mockjdbc.mock.SerializedResultSet;
import io.github.rroyoo.mockjdbc.mock.QueryExecutionStatus;
import net.ttddyy.dsproxy.ExecutionInfo;
import net.ttddyy.dsproxy.QueryInfo;
import net.ttddyy.dsproxy.listener.QueryExecutionListener;
import net.ttddyy.dsproxy.proxy.ResultSetProxyLogicFactory;
import net.ttddyy.dsproxy.proxy.ParameterSetOperation;

import java.sql.ParameterMetaData;
import java.sql.ResultSet;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * datasource-proxy listener that captures JDBC query executions as immutable events.
 */
public final class JdbcQueryCaptureListener implements QueryExecutionListener, AutoCloseable {

    record PendingQueryContext(String sql, List<ParameterMetadata> parameterMetadata, long startTimeMs) {}

    // ThreadLocal shares query context between beforeQuery (listener) and the ResultSetProxyLogicFactory.
    // Cleared by the factory on create(), or by afterQuery as a safety fallback.
    static final ThreadLocal<PendingQueryContext> PENDING_QUERY = new ThreadLocal<>();

    private final CopyOnWriteArrayList<JdbcQueryInterceptedEvent> events = new CopyOnWriteArrayList<>();
    private final Consumer<JdbcQueryInterceptedEvent> eventConsumer;
    private final AsyncMockedQueryEventDispatcher protoDispatcher;
    private final ByteBuddyResultSetWrapperFactory resultSetWrapperFactory;
    private final String datasourceId;

    public JdbcQueryCaptureListener() {
        this("default-datasource", event -> {}, mockedQuery -> {}, AsyncDispatchConfig.defaults());
    }

    public JdbcQueryCaptureListener(String datasourceId) {
        this(datasourceId, event -> {}, mockedQuery -> {}, AsyncDispatchConfig.defaults());
    }

    public JdbcQueryCaptureListener(Consumer<JdbcQueryInterceptedEvent> eventConsumer) {
        this("default-datasource", eventConsumer, mockedQuery -> {}, AsyncDispatchConfig.defaults());
    }

    public JdbcQueryCaptureListener(String datasourceId, Consumer<JdbcQueryInterceptedEvent> eventConsumer) {
        this(datasourceId, eventConsumer, mockedQuery -> {}, AsyncDispatchConfig.defaults());
    }

    public JdbcQueryCaptureListener(Consumer<JdbcQueryInterceptedEvent> eventConsumer,
                                    Consumer<MockedQuery> protoEventConsumer) {
        this("default-datasource", eventConsumer, MockedQueryEventProducer.fromConsumer(protoEventConsumer), AsyncDispatchConfig.defaults());
    }

    public JdbcQueryCaptureListener(String datasourceId,
                                    Consumer<JdbcQueryInterceptedEvent> eventConsumer,
                                    Consumer<MockedQuery> protoEventConsumer) {
        this(datasourceId, eventConsumer, MockedQueryEventProducer.fromConsumer(protoEventConsumer), AsyncDispatchConfig.defaults());
    }

    public JdbcQueryCaptureListener(Consumer<JdbcQueryInterceptedEvent> eventConsumer,
                                    MockedQueryEventProducer externalProducer,
                                    AsyncDispatchConfig asyncDispatchConfig) {
        this("default-datasource", eventConsumer, externalProducer, asyncDispatchConfig);
    }

    public JdbcQueryCaptureListener(String datasourceId,
                                    Consumer<JdbcQueryInterceptedEvent> eventConsumer,
                                    MockedQueryEventProducer externalProducer,
                                    AsyncDispatchConfig asyncDispatchConfig) {
        this.datasourceId = normalizeDatasourceId(datasourceId);
        this.eventConsumer = Objects.requireNonNull(eventConsumer, "eventConsumer is required");
        var producer = Objects.requireNonNull(externalProducer, "externalProducer is required");
        var dispatchConfig = Objects.requireNonNull(asyncDispatchConfig, "asyncDispatchConfig is required");

        this.protoDispatcher = new AsyncMockedQueryEventDispatcher(producer, dispatchConfig);
        this.resultSetWrapperFactory = new ByteBuddyResultSetWrapperFactory(dispatchConfig.rowBatchSize());
    }

    @Override
    public void beforeQuery(ExecutionInfo executionInfo, List<QueryInfo> queryInfoList) {
        // Store query context for the ResultSetProxyLogicFactory (SELECT queries).
        if (queryInfoList != null && !queryInfoList.isEmpty()) {
            var queryInfo = queryInfoList.get(0);
            if (queryInfo != null) {
                var params = extractParameters(queryInfo);
                PENDING_QUERY.set(new PendingQueryContext(
                        queryInfo.getQuery(),
                        toParameterMetadata(params),
                        System.currentTimeMillis()
                ));
            }
        }
    }

    @Override
    public void afterQuery(ExecutionInfo executionInfo, List<QueryInfo> queryInfoList) {
        PENDING_QUERY.remove(); // Safety cleanup; factory should have consumed it for SELECTs.
        if (queryInfoList == null || queryInfoList.isEmpty()) {
            return;
        }

        var elapsedTimeMillis = executionInfo != null ? executionInfo.getElapsedTime() : 0L;
        var success = executionInfo != null && executionInfo.isSuccess();
        var error = executionInfo != null ? executionInfo.getThrowable() : null;

        for (var queryInfo : queryInfoList) {
            if (queryInfo == null) {
                continue;
            }

            var parameters = extractParameters(queryInfo);

            var event = new JdbcQueryInterceptedEvent(
                    datasourceId,
                    queryInfo.getQuery(),
                    parameters,
                    elapsedTimeMillis,
                    success,
                    error
            );

            events.add(event);
            eventConsumer.accept(event);

            emitProtoEvent(executionInfo, queryInfo, parameters);
        }
    }

    public List<JdbcQueryInterceptedEvent> events() {
        return List.copyOf(events);
    }

    public void clear() {
        events.clear();
    }

    /**
     * Returns a {@link ResultSetProxyLogicFactory} that intercepts ResultSet iteration
     * and publishes proto events for SELECT queries via datasource-proxy's ResultSet proxy mechanism.
     */
    public ResultSetProxyLogicFactory resultSetProxyLogicFactory() {
        return new CapturingResultSetProxyLogicFactory(datasourceId, protoDispatcher, resultSetWrapperFactory);
    }

    private void emitProtoEvent(ExecutionInfo executionInfo,
                                QueryInfo queryInfo,
                                List<List<Object>> parameters) {
        // SELECT queries returning a ResultSet are handled by CapturingResultSetProxyLogicFactory
        // during normal JdbcTemplate iteration — no action needed here.
        if (executionInfo != null && executionInfo.getResult() instanceof ResultSet) {
            return;
        }

        var sql = queryInfo.getQuery();
        var parameterMetadata = toParameterMetadata(parameters);
        var elapsedTimeMillis = executionInfo != null ? executionInfo.getElapsedTime() : 0L;
        var success = executionInfo != null && executionInfo.isSuccess();
        var error = executionInfo != null ? executionInfo.getThrowable() : null;


        var result = executionInfo != null ? executionInfo.getResult() : null;
        var serializedResultSet = ByteBuddyResultSetWrapperFactory.resultSetFromUpdateResult(result);
        protoDispatcher.publish(buildMockedQuery(sql, parameterMetadata, serializedResultSet,
                error, elapsedTimeMillis, success, datasourceId,
                asUpdateCount(result), serializedResultSet.getRowsCount()));
    }

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
                .setStatus(success ? QueryExecutionStatus.QUERY_EXECUTION_STATUS_SUCCESS : QueryExecutionStatus.QUERY_EXECUTION_STATUS_ERROR)
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

    private static List<ParameterMetadata> toParameterMetadata(List<List<Object>> parameterGroups) {
        if (parameterGroups == null || parameterGroups.isEmpty()) {
            return List.of();
        }

        var firstGroup = parameterGroups.get(0);
        var result = new ArrayList<ParameterMetadata>(firstGroup.size());

        for (var index = 0; index < firstGroup.size(); index++) {
            var value = firstGroup.get(index);
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

    private static int inferSqlType(Object value) {
        if (value == null) {
            return Types.NULL;
        }
        if (value instanceof String) {
            return Types.VARCHAR;
        }
        if (value instanceof Integer || value instanceof Long || value instanceof Short || value instanceof Byte) {
            return Types.BIGINT;
        }
        if (value instanceof Boolean) {
            return Types.BOOLEAN;
        }
        if (value instanceof Float || value instanceof Double) {
            return Types.DOUBLE;
        }
        if (value instanceof byte[]) {
            return Types.BINARY;
        }
        return Types.JAVA_OBJECT;
    }

    private static List<List<Object>> extractParameters(QueryInfo queryInfo) {
        var parameterGroups = queryInfo.getParametersList();
        if (parameterGroups == null || parameterGroups.isEmpty()) {
            return List.of();
        }

        var result = new ArrayList<List<Object>>(parameterGroups.size());
        for (var group : parameterGroups) {
            if (group == null || group.isEmpty()) {
                result.add(List.of());
                continue;
            }

            var values = new ArrayList<Object>(group.size());
            for (var operation : group) {
                if (operation == null || operation.getArgs() == null || operation.getArgs().length == 0) {
                    continue;
                }

                var args = operation.getArgs();
                var value = args.length > 1 ? args[1] : args[0];
                values.add(value);
            }
            result.add(Collections.unmodifiableList(values));
        }

        return Collections.unmodifiableList(result);
    }

    private static long asUpdateCount(Object result) {
        if (result instanceof Number number) {
            return number.longValue();
        }
        return 0L;
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

    @Override
    public void close() {
        protoDispatcher.close();
    }
}

