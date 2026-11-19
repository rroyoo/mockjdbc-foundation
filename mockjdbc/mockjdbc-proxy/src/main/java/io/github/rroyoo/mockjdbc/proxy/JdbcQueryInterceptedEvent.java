package io.github.rroyoo.mockjdbc.proxy;

import java.util.List;

/**
 * Immutable event captured from instrumented JDBC execution.
 *
 * @param datasourceId logical datasource identifier for this captured query
 * @param sql executed SQL string
 * @param parameters grouped bound parameters captured from JDBC statement execution
 * @param elapsedTimeMillis elapsed execution time in milliseconds
 * @param success execution success flag
 * @param error optional execution error (null when success)
 */
public record JdbcQueryInterceptedEvent(
        String datasourceId,
        String sql,
        List<List<Object>> parameters,
        long elapsedTimeMillis,
        boolean success,
        Throwable error
) {
}
