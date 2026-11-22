package io.github.rroyoo.mockjdbc.mock.statement;

import io.github.rroyoo.mockjdbc.mock.*;
import io.github.rroyoo.mockjdbc.mock.driver.MockConfig;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

final class GrpcMockQueryClient implements AutoCloseable {

    private static final String RPC_TIMEOUT_PROPERTY = "grpcTimeoutMillis";
    private static final long DEFAULT_RPC_TIMEOUT_MILLIS = TimeUnit.SECONDS.toMillis(30);

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

    SerializedResultSet findResultSet(String sql, List<ParameterMetadata> parameters)
            throws SQLException {
        return findMock(sql, parameters).getResultSet();
    }

    static long updateCount(MockedQuery response) throws SQLException {
        var resultSet = response.getResultSet();
        if (response.hasGeneratedKeys() || response.getUpdateCount() != 0) {
            return response.getUpdateCount();
        }
        if (isUpdateCountResultSet(resultSet)) {
            return legacyUpdateCount(resultSet);
        }
        return resultSet.getRowsCount();
    }

    static int updateCountAsInt(MockedQuery response) throws SQLException {
        var updateCount = updateCount(response);
        if (updateCount < Integer.MIN_VALUE || updateCount > Integer.MAX_VALUE) {
            throw new SQLException("Mock update count exceeds the JDBC int range: " + updateCount);
        }
        return (int) updateCount;
    }

    static SerializedResultSet generatedKeys(MockedQuery response) {
        return response.hasGeneratedKeys() ? response.getGeneratedKeys() : response.getResultSet();
    }

    static boolean hasGeneratedKeys(MockedQuery response) {
        return response.hasGeneratedKeys()
                || (response.getResultSet().getMetadataCount() > 0
                        && !isUpdateCountResultSet(response.getResultSet()));
    }

    private static boolean isUpdateCountResultSet(SerializedResultSet resultSet) {
        return resultSet.getMetadataCount() > 0
                && "update_count".equals(resultSet.getMetadata(0).getName());
    }

    private static long legacyUpdateCount(SerializedResultSet resultSet) throws SQLException {
        if (resultSet.getRowsCount() == 0
                || resultSet.getRows(0).getValuesCount() == 0
                || resultSet.getRows(0).getValues(0).getKindCase() != JdbcValue.KindCase.LONG_VAL) {
            throw new SQLException(
                    "Mock update-count result is missing its numeric update_count value");
        }
        return resultSet.getRows(0).getValues(0).getLongVal();
    }

    MockedQuery findMock(String sql, List<ParameterMetadata> parameters) throws SQLException {
        return findMock(sql, parameters, null);
    }

    MockedQuery findMock(String sql, List<ParameterMetadata> parameters, Integer batchIndex)
            throws SQLException {
        var timeoutMillis = rpcTimeoutMillis();
        var activeChannel = acquireChannel();
        try {
            var requestBuilder =
                    QueryLookupRequest.newBuilder()
                            .setSql(SqlNormalizer.normalize(sql))
                            .addAllParameters(parameters);
            if (batchIndex != null) {
                requestBuilder.setBatchIndex(batchIndex);
            }

            var response =
                    MockQueryServiceGrpc.newBlockingStub(activeChannel)
                            .withDeadlineAfter(timeoutMillis, TimeUnit.MILLISECONDS)
                            .findMock(requestBuilder.build());
            if (response.hasError()) {
                throw toSqlException(response.getError());
            }
            return response;
        } catch (StatusRuntimeException e) {
            var message =
                    e.getStatus().getCode() == Status.Code.DEADLINE_EXCEEDED
                            ? "Timed out after "
                                    + timeoutMillis
                                    + " ms querying mock server for SQL: "
                                    + sql
                            : "Failed to query mock server ("
                                    + e.getStatus().getCode()
                                    + ") for SQL: "
                                    + sql;
            throw new SQLException(message, e);
        }
    }

    private long rpcTimeoutMillis() throws SQLException {
        var properties = mockConfig.properties();
        var configuredTimeout =
                properties == null ? null : properties.getProperty(RPC_TIMEOUT_PROPERTY);
        if (configuredTimeout == null) {
            return DEFAULT_RPC_TIMEOUT_MILLIS;
        }

        try {
            var timeoutMillis = Long.parseLong(configuredTimeout);
            if (timeoutMillis > 0) {
                return timeoutMillis;
            }
        } catch (NumberFormatException e) {
            throw new SQLException(RPC_TIMEOUT_PROPERTY + " must be a positive integer", e);
        }
        throw new SQLException(RPC_TIMEOUT_PROPERTY + " must be a positive integer");
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
            var shared =
                    CHANNELS.computeIfAbsent(
                            channelKey,
                            k ->
                                    new SharedChannel(
                                            ManagedChannelBuilder.forAddress(
                                                            mockConfig.mockServer().host(),
                                                            mockConfig.mockServer().port())
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
        if (type == null || type.isBlank()) {
            return fallbackSqlException(message);
        }

        try {
            var clazz = Class.forName(type);
            if (!SQLException.class.isAssignableFrom(clazz)) {
                return fallbackSqlException(message);
            }

            @SuppressWarnings("unchecked")
            var sqlExceptionClass = (Class<? extends SQLException>) clazz;
            return instantiateSqlException(sqlExceptionClass, message);
        } catch (ClassNotFoundException e) {
            return fallbackSqlException(message);
        }
    }

    private static SQLException instantiateSqlException(
            Class<? extends SQLException> exceptionClass, String message) {
        if (message == null || message.isBlank()) {
            try {
                return exceptionClass.getDeclaredConstructor().newInstance();
            } catch (ReflectiveOperationException ignored) {
                return new SQLException();
            }
        }
        try {
            return exceptionClass.getDeclaredConstructor(String.class).newInstance(message);
        } catch (ReflectiveOperationException ignored) {
            return new SQLException(message);
        }
    }

    private static SQLException fallbackSqlException(String message) {
        return message == null || message.isBlank()
                ? new SQLException()
                : new SQLException(message);
    }
}
