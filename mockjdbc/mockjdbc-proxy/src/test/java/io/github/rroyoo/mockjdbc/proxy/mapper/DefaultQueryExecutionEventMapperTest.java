package io.github.rroyoo.mockjdbc.proxy.mapper;

import io.github.rroyoo.mockjdbc.proxy.QueryExecutionEvent;
import net.ttddyy.dsproxy.ExecutionInfo;
import net.ttddyy.dsproxy.QueryInfo;
import net.ttddyy.dsproxy.proxy.ParameterSetOperation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DefaultQueryExecutionEventMapperTest {

    private final DefaultQueryExecutionEventMapper mapper = new DefaultQueryExecutionEventMapper();

    @ParameterizedTest(name = "{index} => parameters={0}")
    @MethodSource("absentParameterScenarios")
    @DisplayName("should map one event when query parameters are absent")
    @SuppressWarnings({"rawtypes", "unchecked"})
    void shouldMapSingleEventWhenParametersAreAbsent(List parameterList) {
        ExecutionInfo executionInfo = mock(ExecutionInfo.class);
        QueryInfo queryInfo = mock(QueryInfo.class);

        when(queryInfo.getQuery()).thenReturn("SELECT 1");
        when(queryInfo.getParametersList()).thenReturn(parameterList);
        when(executionInfo.getElapsedTime()).thenReturn(12L);
        when(executionInfo.isSuccess()).thenReturn(true);

        List<QueryExecutionEvent> events = mapper.map(executionInfo, queryInfo);

        assertEquals(1, events.size());
        assertEquals("SELECT 1", events.get(0).sql());
        assertTrue(events.get(0).parameters().isEmpty());
        assertEquals(12L, events.get(0).executionTimeMillis());
        assertTrue(events.get(0).success());
        assertNull(events.get(0).exception());
    }

    @ParameterizedTest(name = "{index} => mappedValue={0}")
    @CsvSource({"param-1", "param-2"})
    @DisplayName("should map parameter-set value into event payload")
    @SuppressWarnings({"rawtypes", "unchecked"})
    void shouldMapParameterSetValueIntoEvent(String parameterValue) {
        ExecutionInfo executionInfo = mock(ExecutionInfo.class);
        QueryInfo queryInfo = mock(QueryInfo.class);

        ParameterSetOperation parameter = new ParameterSetOperation(null, new Object[]{1, parameterValue});

        when(queryInfo.getQuery()).thenReturn("SELECT * FROM t WHERE id = ?");
        when(queryInfo.getParametersList()).thenReturn((List) List.of(List.of(parameter)));
        when(executionInfo.getElapsedTime()).thenReturn(8L);
        when(executionInfo.isSuccess()).thenReturn(true);

        List<QueryExecutionEvent> events = mapper.map(executionInfo, queryInfo);

        assertEquals(1, events.size());
        assertEquals(List.of(parameterValue), events.get(0).parameters());
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Stream<List> absentParameterScenarios() {
        return Stream.of(
                null,
                List.of()
        );
    }
}
