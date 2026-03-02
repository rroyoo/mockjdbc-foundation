package io.github.rroyoo.mockjdbc.proxy;

import java.util.List;

public record QueryExecutionEvent(
        String sql,
        List<Object> parameters,
        Object result,
        long executionTimeMillis,
        boolean success,
        Throwable exception
) {
}
