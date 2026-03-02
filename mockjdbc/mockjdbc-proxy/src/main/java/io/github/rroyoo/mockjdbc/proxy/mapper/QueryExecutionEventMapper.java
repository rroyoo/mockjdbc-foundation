package io.github.rroyoo.mockjdbc.proxy.mapper;

import io.github.rroyoo.mockjdbc.proxy.QueryExecutionEvent;
import net.ttddyy.dsproxy.ExecutionInfo;
import net.ttddyy.dsproxy.QueryInfo;

import java.util.List;

public interface QueryExecutionEventMapper {

    List<QueryExecutionEvent> map(ExecutionInfo executionInfo, QueryInfo queryInfo);
}
