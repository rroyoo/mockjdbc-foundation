package io.github.rroyoo.mockjdbc.mock.connection;

import io.github.rroyoo.mockjdbc.mock.*;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;

import java.net.PortUnreachableException;
import java.rmi.UnknownHostException;
import java.sql.SQLException;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * gRPC-based implementation of QueryServiceAdapter.
 *
 * Uses gRPC to communicate with a remote MockQueryService for resolving
 * mocked query definitions. Manages the lifecycle of the gRPC ManagedChannel.
 */
final class GrpcQueryServiceAdapter implements QueryServiceAdapter {

    private static final long DEFAULT_KEEP_ALIVE_TIME = 30;
    private static final TimeUnit DEFAULT_KEEP_ALIVE_TIME_UNIT = TimeUnit.SECONDS;
    private static final long DEFAULT_KEEP_ALIVE_TIMEOUT = 10;
    private static final TimeUnit DEFAULT_KEEP_ALIVE_TIMEOUT_TIME_UNIT = TimeUnit.SECONDS;
    private static final int DEFAULT_MAX_RETRY_ATTEMPTS = 3;
    private static final boolean DEFAULT_KEEP_ALIVE_WITHOUT_CALLS = true;
    private static final long DEFAULT_IDLE_TIMEOUT = 10;
    private static final TimeUnit DEFAULT_IDLE_TIMEOUT_TIME_UNIT = TimeUnit.MINUTES;

    private final ManagedChannel managedChannel;
    private final MockQueryServiceGrpc.MockQueryServiceBlockingStub mockQueryServiceBlockingStub;

    public GrpcQueryServiceAdapter(Properties properties) throws PortUnreachableException, UnknownHostException {
        this.managedChannel = buildManagedChannel(properties);
        this.mockQueryServiceBlockingStub = MockQueryServiceGrpc.newBlockingStub(managedChannel);
    }

    private ManagedChannel buildManagedChannel(Properties properties) throws UnknownHostException, PortUnreachableException {
        var host = getHost(properties);
        var port = getPort(properties);
        var keepAliveTime = getKeepAliveTime(properties);
        var keepAliveTimeUnit = getKeepAliveTimeUnit(properties);
        var keepAliveTimeout = getKeepAliveTimeout(properties);
        var keepAliveTimeoutUnit = getKeepAliveTimeoutUnit(properties);
        var idleTimeout = getIdleTimeout(properties);
        var idleTimeoutUnit = getIdleTimeoutUnit(properties);

        return ManagedChannelBuilder.forAddress(host, port)
                .usePlaintext()
                .keepAliveTime(keepAliveTime, keepAliveTimeUnit)
                .keepAliveTimeout(keepAliveTimeout, keepAliveTimeoutUnit)
                .keepAliveWithoutCalls(DEFAULT_KEEP_ALIVE_WITHOUT_CALLS)
                .idleTimeout(idleTimeout, idleTimeoutUnit)
                .enableRetry()
                .maxRetryAttempts(DEFAULT_MAX_RETRY_ATTEMPTS)
                .build();
    }

    private String getHost(Properties properties) throws UnknownHostException {
        return getProperty(MockConnectionProperties.HOST.getKey(), properties, Function.identity())
                .orElseThrow(() -> new UnknownHostException("Host is not specified in the properties."));
    }

    private int getPort(Properties properties) throws PortUnreachableException {
        return getProperty(MockConnectionProperties.PORT.getKey(), properties, Integer::parseInt)
                .orElseThrow(() -> new PortUnreachableException("Port is not specified in the properties."));
    }

    private long getKeepAliveTime(Properties properties) {
        return getPropertyOrDefault(MockConnectionProperties.KEEP_ALIVE_TIME.getKey(), properties, Long::parseLong, DEFAULT_KEEP_ALIVE_TIME);
    }

    private TimeUnit getKeepAliveTimeUnit(Properties properties) {
        return getPropertyOrDefault(MockConnectionProperties.KEEP_ALIVE_TIME_UNIT.getKey(), properties, TimeUnit::valueOf, DEFAULT_KEEP_ALIVE_TIME_UNIT);
    }

    private long getKeepAliveTimeout(Properties properties) {
        return getPropertyOrDefault(MockConnectionProperties.KEEP_ALIVE_TIMEOUT.getKey(), properties, Long::parseLong, DEFAULT_KEEP_ALIVE_TIMEOUT);
    }

    private TimeUnit getKeepAliveTimeoutUnit(Properties properties) {
        return getPropertyOrDefault(MockConnectionProperties.KEEP_ALIVE_TIMEOUT_TIME_UNIT.getKey(), properties, TimeUnit::valueOf, DEFAULT_KEEP_ALIVE_TIMEOUT_TIME_UNIT);
    }

    private long getIdleTimeout(Properties properties) {
        return getPropertyOrDefault(MockConnectionProperties.IDLE_TIMEOUT.getKey(), properties, Long::parseLong, DEFAULT_IDLE_TIMEOUT);
    }

    private TimeUnit getIdleTimeoutUnit(Properties properties) {
        return getPropertyOrDefault(MockConnectionProperties.IDLE_TIMEOUT_TIME_UNIT.getKey(), properties, TimeUnit::valueOf, DEFAULT_IDLE_TIMEOUT_TIME_UNIT);
    }

    private <T> java.util.Optional<T> getProperty(String key, Properties properties, Function<String, T> mapper) {
        var value = properties.getProperty(key);
        if (value == null) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(mapper.apply(value));
    }

    private <T> T getPropertyOrDefault(String key, Properties properties, Function<String, T> mapper, T defaultValue) {
        if (!properties.containsKey(key)) {
            return defaultValue;
        }
        return mapper.apply(properties.getProperty(key));
    }

    @Override
    public void close() {
        if (managedChannel != null && !managedChannel.isShutdown()) {
            try {
                managedChannel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                managedChannel.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

    @Override
    public MockedQuery findMockedQuery(PlainStatement plainStatement) {
        var queryLookupRequest = QueryLookupRequest.newBuilder()
                .setSql(plainStatement.getSql())
                .build();

        return mockQueryServiceBlockingStub.findMock(queryLookupRequest);
    }

    @Override
    public MockedQuery findMockedQuery(PreparedStatement preparedStatement) {
        var queryLookupRequest = QueryLookupRequest.newBuilder()
                .setSql(preparedStatement.getSql())
                .addAllParameters(preparedStatement.getParametersList())
                .build();

        return mockQueryServiceBlockingStub.findMock(queryLookupRequest);
    }

    @Override
    public MockedQuery findMockedQuery(CallableStatement callableStatement) throws SQLException {
        var queryLookupRequest = QueryLookupRequest.newBuilder()
                .setSql(callableStatement.getCallSql())
                .addAllParameters(callableStatement.getParametersList())
                .build();

        return mockQueryServiceBlockingStub.findMock(queryLookupRequest);
    }
}

