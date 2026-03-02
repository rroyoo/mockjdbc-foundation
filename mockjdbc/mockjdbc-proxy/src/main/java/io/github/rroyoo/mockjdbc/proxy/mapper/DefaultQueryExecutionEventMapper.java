package io.github.rroyoo.mockjdbc.proxy.mapper;

import io.github.rroyoo.mockjdbc.proxy.QueryExecutionEvent;
import net.ttddyy.dsproxy.ExecutionInfo;
import net.ttddyy.dsproxy.QueryInfo;

import java.util.List;

public final class DefaultQueryExecutionEventMapper implements QueryExecutionEventMapper {

    @Override
    public List<QueryExecutionEvent> map(ExecutionInfo executionInfo, QueryInfo queryInfo) {

        return queryInfo.getParametersList().stream().map(
                parameterSetOperations ->
                    new QueryExecutionEvent(
                            queryInfo.getQuery(),
                            List.of(),
                            executionInfo.getResult(),
                            executionInfo.getElapsedTime(),
                            executionInfo.isSuccess(),
                            executionInfo.getThrowable()
                    )).toList();
    }
}
