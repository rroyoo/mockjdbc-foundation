package io.github.rroyoo.mockjdbc.proxy;

import io.github.rroyoo.mockjdbc.mock.MockedQuery;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JdbcAgentRegistryTest {

    @AfterEach
    void tearDown() {
        // Unregister any DataSource mocks registered in tests
    }

    @Test
    @DisplayName("Given a registered DataSource, when wrapIfRegistered is called, then Connection is wrapped")
    void shouldWrapConnectionForRegisteredDataSource() throws Exception {
        var dataSource = mock(DataSource.class);
        var conn = mock(Connection.class);
        var stmt = mock(Statement.class);
        when(conn.createStatement()).thenReturn(stmt);

        try (var registration = JdbcAgentRegistry.register(dataSource, "users-primary",
                event -> {}, MockedQueryEventProducer.fromConsumer(q -> {}), AsyncDispatchConfig.defaults())) {

            var wrapped = JdbcAgentRegistry.wrapIfRegistered(dataSource, conn);

            assertNotNull(wrapped);
            // Wrapped connection is a proxy — distinct from the original mock
            assertTrue(wrapped != conn);
            // createStatement() returns a CapturingStatement proxy
            var wrappedStmt = wrapped.createStatement();
            assertNotNull(wrappedStmt);
            assertTrue(wrappedStmt != stmt);
        } finally {
            JdbcAgentRegistry.unregister(dataSource);
        }
    }

    @Test
    @DisplayName("Given an unregistered DataSource, when wrapIfRegistered is called, then original Connection is returned")
    void shouldReturnOriginalConnectionForUnregisteredDataSource() {
        var dataSource = mock(DataSource.class);
        var conn = mock(Connection.class);

        var result = JdbcAgentRegistry.wrapIfRegistered(dataSource, conn);

        assertSame(conn, result);
    }

    @Test
    @DisplayName("Given null inputs to wrapIfRegistered, when called, then original connection is returned")
    void shouldHandleNullInputsGracefully() {
        var conn = mock(Connection.class);
        assertSame(conn, JdbcAgentRegistry.wrapIfRegistered(null, conn));
        assertNull(JdbcAgentRegistry.wrapIfRegistered(null, null));
    }

    @Test
    @DisplayName("Given two registered DataSources, when capturing events from each, then datasource ids are distinct")
    void shouldCaptureTwoDataSourcesWithDistinctIds() throws Exception {
        var usersSource = mock(DataSource.class);
        var ordersSource = mock(DataSource.class);

        var usersConn = mock(Connection.class);
        var ordersConn = mock(Connection.class);
        var usersStmt = mock(PreparedStatement.class);
        var ordersStmt = mock(PreparedStatement.class);
        when(usersConn.prepareStatement("UPDATE users SET active = true")).thenReturn(usersStmt);
        when(ordersConn.prepareStatement("UPDATE orders SET synced = true")).thenReturn(ordersStmt);
        when(usersStmt.executeUpdate()).thenReturn(1);
        when(ordersStmt.executeUpdate()).thenReturn(2);

        var emittedProtoEvents = new CopyOnWriteArrayList<MockedQuery>();
        var latch = new CountDownLatch(2);

        try (var usersReg = JdbcAgentRegistry.register(usersSource, "users-primary",
                event -> {}, MockedQueryEventProducer.fromConsumer(q -> { emittedProtoEvents.add(q); latch.countDown(); }),
                AsyncDispatchConfig.defaults());
             var ordersReg = JdbcAgentRegistry.register(ordersSource, "orders-replica",
                event -> {}, MockedQueryEventProducer.fromConsumer(q -> { emittedProtoEvents.add(q); latch.countDown(); }),
                AsyncDispatchConfig.defaults())) {

            var wrappedUsersConn = JdbcAgentRegistry.wrapIfRegistered(usersSource, usersConn);
            var wrappedOrdersConn = JdbcAgentRegistry.wrapIfRegistered(ordersSource, ordersConn);

            // Execute through wrapped connections
            wrappedUsersConn.prepareStatement("UPDATE users SET active = true").executeUpdate();
            wrappedOrdersConn.prepareStatement("UPDATE orders SET synced = true").executeUpdate();

            assertTrue(latch.await(2, TimeUnit.SECONDS));
            assertEquals(2, emittedProtoEvents.size());
            assertTrue(emittedProtoEvents.stream().anyMatch(e -> "users-primary".equals(e.getDatasourceId())));
            assertTrue(emittedProtoEvents.stream().anyMatch(e -> "orders-replica".equals(e.getDatasourceId())));

        } finally {
            JdbcAgentRegistry.unregister(usersSource);
            JdbcAgentRegistry.unregister(ordersSource);
        }
    }

    @Test
    @DisplayName("Given a registered DataSource, when unregistered, then subsequent wrapIfRegistered returns original connection")
    void shouldReturnOriginalConnectionAfterUnregister() throws Exception {
        var dataSource = mock(DataSource.class);
        var conn = mock(Connection.class);

        JdbcAgentRegistry.register(dataSource, "orders-replica", event -> {},
                MockedQueryEventProducer.fromConsumer(q -> {}), AsyncDispatchConfig.defaults()).close();
        JdbcAgentRegistry.unregister(dataSource);

        assertSame(conn, JdbcAgentRegistry.wrapIfRegistered(dataSource, conn));
    }

    @Test
    @DisplayName("Given a JdbcCaptureRegistration, when captureUpdate is called on inner capture, then local events are visible through registration")
    void shouldExposeEventsViaRegistration() {
        var dataSource = mock(DataSource.class);

        try (var registration = JdbcAgentRegistry.register(dataSource, "billing-primary",
                event -> {}, MockedQueryEventProducer.fromConsumer(q -> {}), AsyncDispatchConfig.defaults())) {

            registration.capture().captureUpdate("INSERT INTO audit VALUES (?)",
                    java.util.List.of("user-1"), 1L, 5L, true, null);

            assertEquals(1, registration.events().size());
            assertEquals("billing-primary", registration.events().get(0).datasourceId());
            assertEquals("INSERT INTO audit VALUES (?)", registration.events().get(0).sql());

        } finally {
            JdbcAgentRegistry.unregister(dataSource);
        }
    }

    @Test
    @DisplayName("Given JdbcCaptureRegistration.standalone, when used without DataSource, then it works as an independent capture unit")
    void shouldCreateStandaloneRegistrationWithoutDataSource() throws Exception {
        var protoEvent = new AtomicReference<MockedQuery>();
        var latch = new CountDownLatch(1);

        try (var registration = JdbcCaptureRegistration.standalone(
                "standalone-ds",
                event -> {},
                MockedQueryEventProducer.fromConsumer(q -> { protoEvent.set(q); latch.countDown(); }),
                AsyncDispatchConfig.defaults())) {

            registration.capture().captureUpdate("SELECT 1", java.util.List.of(), 0L, 1L, true, null);

            assertTrue(latch.await(2, TimeUnit.SECONDS));
            assertEquals("standalone-ds", protoEvent.get().getDatasourceId());
        }
    }
}
