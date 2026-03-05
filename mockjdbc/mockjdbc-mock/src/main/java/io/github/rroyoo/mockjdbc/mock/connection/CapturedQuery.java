package io.github.rroyoo.mockjdbc.mock.connection;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Immutable snapshot of an executed SQL query.
 */
public record CapturedQuery(
    String sql,
    List<Object> orderedParams,
    StatementType statementType,
    Instant timestamp
) {

    public CapturedQuery {
        sql = Objects.requireNonNull(sql, "sql cannot be null");
        orderedParams = List.copyOf(Objects.requireNonNull(orderedParams, "orderedParams cannot be null"));
        statementType = Objects.requireNonNull(statementType, "statementType cannot be null");
        timestamp = Objects.requireNonNull(timestamp, "timestamp cannot be null");
    }

    public enum StatementType {
        PLAIN,
        PREPARED,
        CALLABLE
    }
}

