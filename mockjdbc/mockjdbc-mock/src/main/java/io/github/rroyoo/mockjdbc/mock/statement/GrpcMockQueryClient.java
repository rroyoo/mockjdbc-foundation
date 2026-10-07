package io.github.rroyoo.mockjdbc.mock.statement;

import io.github.rroyoo.mockjdbc.mock.*;
import io.github.rroyoo.mockjdbc.mock.driver.MockConfig;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

final class GrpcMockQueryClient implements AutoCloseable {

    /**
     * Reference-counted channel per host:port. Each client acquires its channel on first use and
     * releases it on {@link #close()}; the channel is shut down when the last client releases it,
     * so no channel outlives the connections that use it.
     */
    private static final Map<String, SharedChannel> CHANNELS = new HashMap<>();

    private final MockConfig mockConfig;
    private final String channelKey;
    private ManagedChannel channel;
    private boolean closed;

    GrpcMockQueryClient(MockConfig mockConfig) {
        this.mockConfig = mockConfig;
        this.channelKey = mockConfig.mockServer().host() + ":" + mockConfig.mockServer().port();
    }

    /** Exposed for test teardown so channels can be shut down cleanly. */
    static void shutdownAll() {
        synchronized (CHANNELS) {
            CHANNELS.values().forEach(shared -> shutdown(shared.channel));
            CHANNELS.clear();
        }
    }

    /** Number of channels currently retained; visible for tests. */
    static int openChannelCount() {
        synchronized (CHANNELS) {
            return CHANNELS.size();
        }
    }

    /** Releases this client's reference on the shared channel. Idempotent. */
    @Override
    public synchronized void close() {
        if (closed) {
            return;
        }
        closed = true;
        if (channel != null) {
            channel = null;
            release(channelKey);
        }
    }

    SerializedResultSet findResultSet(String sql, List<ParameterMetadata> parameters) throws SQLException {
        var activeChannel = acquireChannel();
        try {
            var request = QueryLookupRequest.newBuilder()
                    .setSql(sql)
                    .addAllParameters(parameters)
                    .build();

            var response = MockQueryServiceGrpc.newBlockingStub(activeChannel).findMock(request);
            if (response.hasError()) {
                throw toSqlException(response.getError());
            }
            return response.getResultSet();
        } catch (StatusRuntimeException e) {
            throw new SQLException("Failed to query mock server for SQL: " + sql, e);
        }
    }

    private synchronized ManagedChannel acquireChannel() throws SQLException {
        if (closed) {
            throw new SQLException("Mock query client is closed");
        }
        if (channel == null) {
            channel = retain();
        }
        return channel;
    }

    private ManagedChannel retain() {
        synchronized (CHANNELS) {
            var shared = CHANNELS.computeIfAbsent(channelKey, k -> new SharedChannel(
                    ManagedChannelBuilder
                            .forAddress(mockConfig.mockServer().host(), mockConfig.mockServer().port())
                            .usePlaintext()
                            .build()));
            shared.references++;
            return shared.channel;
        }
    }

    private static void release(String key) {
        synchronized (CHANNELS) {
            var shared = CHANNELS.get(key);
            if (shared != null && --shared.references <= 0) {
                CHANNELS.remove(key);
                shutdown(shared.channel);
            }
        }
    }

    private static void shutdown(ManagedChannel channel) {
        channel.shutdownNow();
        try {
            channel.awaitTermination(1, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static final class SharedChannel {
        private final ManagedChannel channel;
        private int references;

        private SharedChannel(ManagedChannel channel) {
            this.channel = channel;
        }
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
