package io.github.rroyoo.mockjdbc.mock.connection;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Immutable value object representing a query execution captured by the mock connection.
 */
public record CapturedQuery(
    String sql,
    List<Object> orderedParams,
    StatementType statementType,
    Instant timestamp
) {

    public enum StatementType {
        PLAIN,
        PREPARED
    }

    public CapturedQuery {
        Objects.requireNonNull(sql, "sql must not be null");
        Objects.requireNonNull(orderedParams, "orderedParams must not be null");
        Objects.requireNonNull(statementType, "statementType must not be null");
        Objects.requireNonNull(timestamp, "timestamp must not be null");
        orderedParams = List.copyOf(orderedParams);
    }

    public static CapturedQuery plain(String sql) {
        return new CapturedQuery(sql, List.of(), StatementType.PLAIN, Instant.now());
    }

    public static CapturedQuery prepared(String sql, List<Object> orderedParams) {
        return new CapturedQuery(sql, orderedParams, StatementType.PREPARED, Instant.now());
    }
}

