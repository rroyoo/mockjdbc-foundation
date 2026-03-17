package io.github.rroyoo.mockjdbc.proxy;

import io.github.rroyoo.mockjdbc.mock.CallableStatement;
import io.github.rroyoo.mockjdbc.mock.MockedQuery;
import io.github.rroyoo.mockjdbc.mock.ParameterMetadata;
import io.github.rroyoo.mockjdbc.mock.PlainStatement;
import io.github.rroyoo.mockjdbc.mock.PreparedStatement;
import io.github.rroyoo.mockjdbc.mock.SerializedResultSet;
import net.ttddyy.dsproxy.ExecutionInfo;
import net.ttddyy.dsproxy.QueryInfo;
import net.ttddyy.dsproxy.listener.QueryExecutionListener;
import net.ttddyy.dsproxy.proxy.ParameterSetOperation;

import java.sql.ParameterMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * datasource-proxy listener that captures JDBC query executions as immutable events.
 */
public final class JdbcQueryCaptureListener implements QueryExecutionListener, AutoCloseable {

    private final CopyOnWriteArrayList<JdbcQueryInterceptedEvent> events = new CopyOnWriteArrayList<>();
    private final Consumer<JdbcQueryInterceptedEvent> eventConsumer;
    private final AsyncMockedQueryEventDispatcher protoDispatcher;
    private final ByteBuddyResultSetWrapperFactory resultSetWrapperFactory;

    public JdbcQueryCaptureListener() {
        this(event -> {}, mockedQuery -> {}, AsyncDispatchConfig.defaults());
    }

    public JdbcQueryCaptureListener(Consumer<JdbcQueryInterceptedEvent> eventConsumer) {
        this(eventConsumer, mockedQuery -> {}, AsyncDispatchConfig.defaults());
    }

    public JdbcQueryCaptureListener(Consumer<JdbcQueryInterceptedEvent> eventConsumer,
                                    Consumer<MockedQuery> protoEventConsumer) {
        this(eventConsumer, MockedQueryEventProducer.fromConsumer(protoEventConsumer), AsyncDispatchConfig.defaults());
    }

    public JdbcQueryCaptureListener(Consumer<JdbcQueryInterceptedEvent> eventConsumer,
                                    MockedQueryEventProducer externalProducer,
                                    AsyncDispatchConfig asyncDispatchConfig) {
        this.eventConsumer = Objects.requireNonNull(eventConsumer, "eventConsumer is required");
        var producer = Objects.requireNonNull(externalProducer, "externalProducer is required");
        var dispatchConfig = Objects.requireNonNull(asyncDispatchConfig, "asyncDispatchConfig is required");

        this.protoDispatcher = new AsyncMockedQueryEventDispatcher(producer, dispatchConfig);
        this.resultSetWrapperFactory = new ByteBuddyResultSetWrapperFactory(dispatchConfig.rowBatchSize());
    }

    @Override
    public void beforeQuery(ExecutionInfo executionInfo, List<QueryInfo> queryInfoList) {
        // Intentionally no-op. We emit deterministic events only after execution.
    }

    @Override
    public void afterQuery(ExecutionInfo executionInfo, List<QueryInfo> queryInfoList) {
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

    private void emitProtoEvent(ExecutionInfo executionInfo,
                                QueryInfo queryInfo,
                                List<List<Object>> parameters) {
        var sql = queryInfo.getQuery();
        var parameterMetadata = toParameterMetadata(parameters);

        if (executionInfo != null && executionInfo.getResult() instanceof ResultSet resultSet) {
            try {
                var wrapped = resultSetWrapperFactory.wrap(resultSet, serialized ->
                        protoDispatcher.publish(buildMockedQuery(sql, parameterMetadata, serialized,
                                executionInfo != null ? executionInfo.getThrowable() : null)));
                executionInfo.setResult(wrapped);
                return;
            } catch (SQLException e) {
                throw new RuntimeException("Failed to wrap ResultSet for interception", e);
            }
        }

        var serializedResultSet = ByteBuddyResultSetWrapperFactory.resultSetFromUpdateResult(
                executionInfo != null ? executionInfo.getResult() : null);
        protoDispatcher.publish(buildMockedQuery(sql, parameterMetadata, serializedResultSet,
                executionInfo != null ? executionInfo.getThrowable() : null));
    }

    private static MockedQuery buildMockedQuery(String sql,
                                                List<ParameterMetadata> parameters,
                                                SerializedResultSet serializedResultSet,
                                                Throwable error) {
        var builder = MockedQuery.newBuilder().setResultSet(serializedResultSet);
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

    @Override
    public void close() {
        protoDispatcher.close();
    }
}

