package io.github.rroyoo.mockjdbc.users.config;

import io.github.rroyoo.mockjdbc.mock.*;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.stub.StreamObserver;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.sql.Types;
import java.util.Comparator;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Configuration
@Profile("mockjdbc")
public class MockJdbcGrpcStubConfig {

    private final int port;
    private final InMemoryMockQueryService queryService = new InMemoryMockQueryService();
    private Server server;

    public MockJdbcGrpcStubConfig(@Value("${mockjdbc.grpc.port:50051}") int port) {
        this.port = port;
    }

    @PostConstruct
    void start() throws Exception {
        server = ServerBuilder.forPort(port)
                .addService(queryService)
                .build()
                .start();
    }

    @PreDestroy
    void stop() {
        if (server != null) {
            server.shutdownNow();
        }
    }

    private static final class InMemoryMockQueryService extends MockQueryServiceGrpc.MockQueryServiceImplBase {

        private final AtomicLong sequence = new AtomicLong(2L);
        private final Map<Long, UserData> users = new ConcurrentHashMap<>();

        InMemoryMockQueryService() {
            users.put(1L, new UserData(1L, "Ada Lovelace", "ada@mockjdbc.dev"));
            users.put(2L, new UserData(2L, "Grace Hopper", "grace@mockjdbc.dev"));
        }

        @Override
        public void findMock(QueryLookupRequest request, StreamObserver<MockedQuery> responseObserver) {
            String normalizedSql = normalizeSql(request.getSql());
            SerializedResultSet resultSet = route(request, normalizedSql);
            responseObserver.onNext(MockedQuery.newBuilder().setResultSet(resultSet).build());
            responseObserver.onCompleted();
        }

        private SerializedResultSet route(QueryLookupRequest request, String sql) {
            if (sql.startsWith("select id, name, email from users where id = ?")) {
                long id = readLongParameter(request, 1);
                UserData user = users.get(id);
                return usersResultSet(user == null ? java.util.List.of() : java.util.List.of(user));
            }

            if (sql.startsWith("select id, name, email from users where email = ?")) {
                String email = readStringParameter(request, 1);
                UserData user = users.values().stream()
                        .filter(it -> Objects.equals(it.email(), email))
                        .findFirst()
                        .orElse(null);
                return usersResultSet(user == null ? java.util.List.of() : java.util.List.of(user));
            }

            if (sql.startsWith("select id, name, email from users order by id")) {
                var orderedUsers = users.values().stream()
                        .sorted(Comparator.comparingLong(UserData::id))
                        .toList();
                return usersResultSet(orderedUsers);
            }

            if (sql.startsWith("insert into users(name, email) values (?, ?)")) {
                String name = readStringParameter(request, 1);
                String email = readStringParameter(request, 2);
                long id = sequence.incrementAndGet();
                users.put(id, new UserData(id, name, email));
                return updateCountResultSet(1);
            }

            if (sql.startsWith("update users set name = ?, email = ? where id = ?")) {
                String name = readStringParameter(request, 1);
                String email = readStringParameter(request, 2);
                long id = readLongParameter(request, 3);
                if (!users.containsKey(id)) {
                    return updateCountResultSet(0);
                }
                users.put(id, new UserData(id, name, email));
                return updateCountResultSet(1);
            }

            if (sql.startsWith("delete from users where id = ?")) {
                long id = readLongParameter(request, 1);
                return updateCountResultSet(users.remove(id) == null ? 0 : 1);
            }

            return usersResultSet(java.util.List.of());
        }

        private static String normalizeSql(String sql) {
            return sql == null ? "" : sql.trim().toLowerCase().replaceAll("\\s+", " ");
        }

        private static String readStringParameter(QueryLookupRequest request, int index) {
            var parameter = request.getParametersList().stream()
                    .filter(it -> it.getIndex() == index)
                    .findFirst()
                    .orElse(index - 1 < request.getParametersCount() ? request.getParameters(index - 1) : null);
            if (parameter == null) {
                return null;
            }
            return switch (parameter.getValue().getKindCase()) {
                case STRING_VAL -> parameter.getValue().getStringVal();
                case LONG_VAL -> String.valueOf(parameter.getValue().getLongVal());
                case KIND_NOT_SET, IS_NULL, BOOL_VAL, BYTES_VAL, DECIMAL_VAL, DOUBLE_VAL, TIMESTAMP_VAL -> null;
            };
        }

        private static long readLongParameter(QueryLookupRequest request, int index) {
            String value = readStringParameter(request, index);
            if (value == null) {
                var parameter = request.getParametersList().stream()
                        .filter(it -> it.getIndex() == index)
                        .findFirst()
                        .orElse(index - 1 < request.getParametersCount() ? request.getParameters(index - 1) : null);
                if (parameter != null && parameter.getValue().getKindCase() == JdbcValue.KindCase.LONG_VAL) {
                    return parameter.getValue().getLongVal();
                }
                return 0L;
            }
            return Long.parseLong(value);
        }

        private static SerializedResultSet updateCountResultSet(int count) {
            var builder = SerializedResultSet.newBuilder();
            for (int i = 0; i < count; i++) {
                builder.addRows(Row.newBuilder().build());
            }
            return builder.build();
        }

        private static SerializedResultSet usersResultSet(java.util.List<UserData> users) {
            var builder = SerializedResultSet.newBuilder()
                    .addMetadata(ColumnMetadata.newBuilder().setName("id").setLabel("id").setSqlType(Types.BIGINT).setTypeName("BIGINT").build())
                    .addMetadata(ColumnMetadata.newBuilder().setName("name").setLabel("name").setSqlType(Types.VARCHAR).setTypeName("VARCHAR").build())
                    .addMetadata(ColumnMetadata.newBuilder().setName("email").setLabel("email").setSqlType(Types.VARCHAR).setTypeName("VARCHAR").build());

            for (var user : users) {
                builder.addRows(Row.newBuilder()
                        .addValues(JdbcValue.newBuilder().setLongVal(user.id()).build())
                        .addValues(JdbcValue.newBuilder().setStringVal(user.name()).build())
                        .addValues(JdbcValue.newBuilder().setStringVal(user.email()).build())
                        .build());
            }

            return builder.build();
        }
    }

    private record UserData(long id, String name, String email) {
    }
}

