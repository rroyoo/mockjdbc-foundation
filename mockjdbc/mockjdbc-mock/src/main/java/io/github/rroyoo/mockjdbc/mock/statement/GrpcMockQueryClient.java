package io.github.rroyoo.mockjdbc.mock.statement;

import io.github.rroyoo.mockjdbc.mock.MockQueryServiceGrpc;
import io.github.rroyoo.mockjdbc.mock.MockedQuery;
import io.github.rroyoo.mockjdbc.mock.ParameterMetadata;
import io.github.rroyoo.mockjdbc.mock.QueryLookupRequest;
import io.github.rroyoo.mockjdbc.mock.SerializedResultSet;
import io.github.rroyoo.mockjdbc.mock.driver.MockConfig;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;

import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.TimeUnit;

final class GrpcMockQueryClient {

    private final MockConfig mockConfig;

    GrpcMockQueryClient(MockConfig mockConfig) {
        this.mockConfig = mockConfig;
    }

    SerializedResultSet findResultSet(String sql, List<ParameterMetadata> parameters) throws SQLException {
        ManagedChannel channel = null;
        try {
            channel = ManagedChannelBuilder
                    .forAddress(mockConfig.mockServer().host(), mockConfig.mockServer().port())
                    .usePlaintext()
                    .build();

            var request = QueryLookupRequest.newBuilder()
                    .setSql(sql)
                    .addAllParameters(parameters)
                    .build();

            MockedQuery response = MockQueryServiceGrpc.newBlockingStub(channel).findMock(request);
            return response.getResultSet();
        } catch (StatusRuntimeException e) {
            throw new SQLException("Failed to query mock server for SQL: " + sql, e);
        } finally {
            if (channel != null) {
                channel.shutdownNow();
                try {
                    channel.awaitTermination(1, TimeUnit.SECONDS);
                } catch (InterruptedException interruptedException) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }
}

