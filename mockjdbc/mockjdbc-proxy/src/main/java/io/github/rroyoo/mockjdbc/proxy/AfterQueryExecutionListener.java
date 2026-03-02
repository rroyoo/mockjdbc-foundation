package io.github.rroyoo.mockjdbc.proxy;

import io.github.rroyoo.mockjdbc.proxy.mapper.QueryExecutionEventMapper;
import net.ttddyy.dsproxy.ExecutionInfo;
import net.ttddyy.dsproxy.QueryInfo;
import net.ttddyy.dsproxy.listener.QueryExecutionListener;

import java.util.List;

public final class AfterQueryExecutionListener implements QueryExecutionListener {

    private final QueryExecutionEventMapper queryExecutionEventMapper;
    private final QueryExecutionEventListener queryExecutionEventListener;

    public AfterQueryExecutionListener(QueryExecutionEventMapper queryExecutionEventMapper, QueryExecutionEventListener queryExecutionEventListener) {
        this.queryExecutionEventMapper = queryExecutionEventMapper;
        this.queryExecutionEventListener = queryExecutionEventListener;
    }

    @Override
    public void beforeQuery(ExecutionInfo executionInfo, List<QueryInfo> list) {
        throw new UnsupportedOperationException("The beforeQuery method is not supported in AfterQueryExecutionListener.");
    }

    @Override
    public void afterQuery(ExecutionInfo executionInfo, List<QueryInfo> list) {

        list.forEach(queryInfo -> {
            var queryExecutionEvents = queryExecutionEventMapper.map(executionInfo, queryInfo);

            queryExecutionEvents.forEach(queryExecutionEventListener::onQueryExecutionEvent);
        });
    }
}
