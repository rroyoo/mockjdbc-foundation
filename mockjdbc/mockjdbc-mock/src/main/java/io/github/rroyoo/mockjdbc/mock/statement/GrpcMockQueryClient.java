package io.github.rroyoo.mockjdbc.mock.statement;

import io.github.rroyoo.mockjdbc.mock.*;
import io.github.rroyoo.mockjdbc.mock.driver.MockConfig;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;

import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

final class GrpcMockQueryClient {

    /**
     * One shared channel per host:port. gRPC channels are designed to be long-lived and
     * multiplexed; creating a new channel per query wastes connection setup + teardown time.
     */
    private static final ConcurrentHashMap<String, ManagedChannel> CHANNEL_CACHE =
            new ConcurrentHashMap<>();

    private final MockConfig mockConfig;

    GrpcMockQueryClient(MockConfig mockConfig) {
        this.mockConfig = mockConfig;
    }

    /** Exposed for test teardown so channels can be shut down cleanly. */
    static void shutdownAll() {
        CHANNEL_CACHE.values().forEach(ch -> {
            ch.shutdownNow();
            try { ch.awaitTermination(1, TimeUnit.SECONDS); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        });
        CHANNEL_CACHE.clear();
    }

    SerializedResultSet findResultSet(String sql, List<ParameterMetadata> parameters) throws SQLException {
        var channel = getOrCreateChannel();
        try {
            var request = QueryLookupRequest.newBuilder()
                    .setSql(sql)
                    .addAllParameters(parameters)
                    .build();

            var response = MockQueryServiceGrpc.newBlockingStub(channel).findMock(request);
            if (response.hasError()) {
                throw toSqlException(response.getError());
            }
            return response.getResultSet();
        } catch (StatusRuntimeException e) {
            throw new SQLException("Failed to query mock server for SQL: " + sql, e);
        }
    }

    private ManagedChannel getOrCreateChannel() {
        var key = mockConfig.mockServer().host() + ":" + mockConfig.mockServer().port();
        return CHANNEL_CACHE.computeIfAbsent(key, k ->
                ManagedChannelBuilder
                        .forAddress(mockConfig.mockServer().host(), mockConfig.mockServer().port())
                        .usePlaintext()
                        .build()
        );
    }

    private static SQLException toSqlException(QueryError error) {
        var type = error.getType();
        var message = error.getMessage();
        var hasMessage = message != null && !message.isBlank();

        if (type == null || type.isBlank()) {
            return hasMessage ? new SQLException(message) : new SQLException();
        }

        try {
            var clazz = Class.forName(type);
            if (!SQLException.class.isAssignableFrom(clazz)) {
                return hasMessage ? new SQLException(message) : new SQLException();
            }

            @SuppressWarnings("unchecked")
            var sqlExceptionClass = (Class<? extends SQLException>) clazz;

            if (!hasMessage) {
                try {
                    return sqlExceptionClass.getDeclaredConstructor().newInstance();
                } catch (ReflectiveOperationException ignored) {
                    return new SQLException();
                }
            }

            try {
                return sqlExceptionClass.getDeclaredConstructor(String.class).newInstance(message);
            } catch (ReflectiveOperationException ignored) {
                return new SQLException(message);
            }
        } catch (ClassNotFoundException e) {
            return hasMessage ? new SQLException(message) : new SQLException();
        }
    }
}
