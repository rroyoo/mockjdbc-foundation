package io.github.rroyoo.mockjdbc.proxy.mapper;

import io.github.rroyoo.mockjdbc.proxy.QueryExecutionEvent;
import net.ttddyy.dsproxy.ExecutionInfo;
import net.ttddyy.dsproxy.QueryInfo;
import net.ttddyy.dsproxy.proxy.ParameterSetOperation;

import java.util.List;

public final class DefaultQueryExecutionEventMapper implements QueryExecutionEventMapper {

    @Override
    public List<QueryExecutionEvent> map(ExecutionInfo executionInfo, QueryInfo queryInfo) {
        var parameterSets = queryInfo.getParametersList();

        if (parameterSets == null || parameterSets.isEmpty()) {
            return List.of(toEvent(executionInfo, queryInfo, List.of()));
        }

        return parameterSets.stream()
                .map(parameterSet -> parameterSet.stream().map(this::toParameterValue).toList())
                .map(parameters -> toEvent(executionInfo, queryInfo, parameters))
                .toList();
    }

    private Object toParameterValue(ParameterSetOperation operation) {
        var args = operation.getArgs();

        if (args == null || args.length == 0) {
            return null;
        }

        // datasource-proxy stores parameter index first and value last for setX operations.
        return args[args.length - 1];
    }

    private QueryExecutionEvent toEvent(ExecutionInfo executionInfo, QueryInfo queryInfo, List<Object> parameters) {
        return new QueryExecutionEvent(
                queryInfo.getQuery(),
                parameters,
                executionInfo.getResult(),
                executionInfo.getElapsedTime(),
                executionInfo.isSuccess(),
                executionInfo.getThrowable()
        );
    }
}
