package io.github.rroyoo.mockjdbc.mock.driver;

import io.github.rroyoo.mockjdbc.mock.ColumnMetadata;
import io.github.rroyoo.mockjdbc.mock.JdbcValue;
import io.github.rroyoo.mockjdbc.mock.MockQueryServiceGrpc;
import io.github.rroyoo.mockjdbc.mock.MockedQuery;
import io.github.rroyoo.mockjdbc.mock.QueryLookupRequest;
import io.github.rroyoo.mockjdbc.mock.Row;
import io.github.rroyoo.mockjdbc.mock.SerializedResultSet;
import io.grpc.Server;
import io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Types;
import java.util.Collections;
import java.util.ServiceLoader;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DriverManagerDiscoveryTest {

    private final AtomicReference<QueryLookupRequest> lastRequest = new AtomicReference<>();
    private Server server;

    @BeforeEach
    void setUp() throws Exception {
        server = NettyServerBuilder.forPort(0)
                .addService(new MockQueryServiceGrpc.MockQueryServiceImplBase() {
                    @Override
                    public void findMock(QueryLookupRequest request, StreamObserver<MockedQuery> responseObserver) {
                        lastRequest.set(request);
                        responseObserver.onNext(MockedQuery.newBuilder().setResultSet(greetingResultSet()).build());
                        responseObserver.onCompleted();
                    }
                })
                .build()
                .start();
    }

    @AfterEach
    void tearDown() throws Exception {
        server.shutdownNow();
        server.awaitTermination();
    }

    @Test
    @DisplayName("Given the driver on the classpath, when ServiceLoader looks up java.sql.Driver, then MockDriver is discovered")
    void shouldDiscoverMockDriverThroughServiceLoader() {
        var drivers = ServiceLoader.load(Driver.class).stream()
                .map(ServiceLoader.Provider::type)
                .toList();

        assertTrue(drivers.contains(MockDriver.class));
    }

    @Test
    @DisplayName("Given the driver on the classpath, when DriverManager resolves a jdbc:mock URL, then MockDriver is selected without manual registration")
    void shouldResolveMockDriverFromDriverManager() throws Exception {
        var driver = DriverManager.getDriver(url());

        assertInstanceOf(MockDriver.class, driver);
        assertTrue(Collections.list(DriverManager.getDrivers()).stream().anyMatch(MockDriver.class::isInstance));
    }

    @Test
    @DisplayName("Given a jdbc:mock URL, when DriverManager connects and queries, then the result comes from the gRPC backend")
    void shouldRunQueryEndToEndThroughDriverManager() throws Exception {
        try (var connection = DriverManager.getConnection(url());
             var statement = connection.prepareStatement("SELECT greeting FROM hello WHERE id = ?")) {
            statement.setInt(1, 7);

            try (var resultSet = statement.executeQuery()) {
                assertTrue(resultSet.next());
                assertEquals("hello", resultSet.getString("greeting"));
                assertTrue(connection.isValid(1));
                assertEquals("MockJDBC", connection.getMetaData().getDatabaseProductName());
            }
        }

        assertEquals("SELECT greeting FROM hello WHERE id = ?", lastRequest.get().getSql());
        assertEquals(1, lastRequest.get().getParametersCount());
    }

    @Test
    @DisplayName("Given a URL for another database, when DriverManager is asked for a driver, then MockDriver does not claim it")
    void shouldNotClaimForeignUrls() {
        var exception = assertThrows(SQLException.class, () -> DriverManager.getConnection("jdbc:unknown://localhost:1234"));

        assertEquals("08001", exception.getSQLState());
    }

    private String url() {
        return "jdbc:mock://localhost:" + server.getPort();
    }

    private static SerializedResultSet greetingResultSet() {
        return SerializedResultSet.newBuilder()
                .addMetadata(ColumnMetadata.newBuilder()
                        .setName("greeting").setLabel("greeting").setSqlType(Types.VARCHAR).setTypeName("VARCHAR").build())
                .addRows(Row.newBuilder().addValues(JdbcValue.newBuilder().setStringVal("hello").build()).build())
                .build();
    }
}
