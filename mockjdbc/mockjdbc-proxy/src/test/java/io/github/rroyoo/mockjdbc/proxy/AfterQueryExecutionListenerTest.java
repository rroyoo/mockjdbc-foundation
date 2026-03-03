package io.github.rroyoo.mockjdbc.proxy;

import io.github.rroyoo.mockjdbc.proxy.mapper.QueryExecutionEventMapper;
import net.ttddyy.dsproxy.ExecutionInfo;
import net.ttddyy.dsproxy.QueryInfo;
import net.ttddyy.dsproxy.listener.QueryExecutionListener;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AfterQueryExecutionListenerTest {

    @Test
    @DisplayName("should throw when beforeQuery is called")
    void shouldThrowOnBeforeQuery() throws Exception {
        QueryExecutionEventListener mockListener = mock(QueryExecutionEventListener.class);
        AfterQueryExecutionListener listener = new AfterQueryExecutionListener(null, mockListener);

        ExecutionInfo execInfo = mock(ExecutionInfo.class);

        assertThrows(UnsupportedOperationException.class, () -> listener.beforeQuery(execInfo, List.of()));
    }

    @Test
    @DisplayName("should invoke event listener on afterQuery with single query")
    void shouldInvokeListenerOnAfterQuery() throws Exception {
        QueryExecutionEventListener mockEventListener = mock(QueryExecutionEventListener.class);
        QueryExecutionEventMapper mockMapper = mock(QueryExecutionEventMapper.class);

        AfterQueryExecutionListener listener = new AfterQueryExecutionListener(mockMapper, mockEventListener);

        ExecutionInfo execInfo = mock(ExecutionInfo.class);
        QueryInfo queryInfo = mock(QueryInfo.class);
        QueryExecutionEvent event = new QueryExecutionEvent("SELECT 1", List.of(), null, 100L, true, null);

        when(mockMapper.map(execInfo, queryInfo)).thenReturn(List.of(event));

        listener.afterQuery(execInfo, List.of(queryInfo));

        verify(mockEventListener).onQueryExecutionEvent(event);
    }

    @Test
    @DisplayName("should process multiple queries in execution")
    void shouldProcessMultipleQueries() throws Exception {
        QueryExecutionEventListener mockEventListener = mock(QueryExecutionEventListener.class);
        QueryExecutionEventMapper mockMapper = mock(QueryExecutionEventMapper.class);

        AfterQueryExecutionListener listener = new AfterQueryExecutionListener(mockMapper, mockEventListener);

        ExecutionInfo execInfo = mock(ExecutionInfo.class);
        QueryInfo query1 = mock(QueryInfo.class);
        QueryInfo query2 = mock(QueryInfo.class);

        QueryExecutionEvent event1 = new QueryExecutionEvent("SELECT 1", List.of(), null, 50L, true, null);
        QueryExecutionEvent event2 = new QueryExecutionEvent("SELECT 2", List.of(), null, 75L, true, null);

        when(mockMapper.map(execInfo, query1)).thenReturn(List.of(event1));
        when(mockMapper.map(execInfo, query2)).thenReturn(List.of(event2));

        listener.afterQuery(execInfo, List.of(query1, query2));

        ArgumentCaptor<QueryExecutionEvent> captor = ArgumentCaptor.forClass(QueryExecutionEvent.class);
        verify(mockEventListener, times(2)).onQueryExecutionEvent(captor.capture());

        List<QueryExecutionEvent> captured = captor.getAllValues();
        assertEquals(2, captured.size());
    }
}

