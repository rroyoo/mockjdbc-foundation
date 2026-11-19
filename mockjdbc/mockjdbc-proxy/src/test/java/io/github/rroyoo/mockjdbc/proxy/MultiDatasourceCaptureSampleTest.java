package io.github.rroyoo.mockjdbc.proxy;

import io.github.rroyoo.mockjdbc.mock.MockedQuery;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.PreparedStatement;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Verifies that two independently registered DataSources emit events with distinct datasource ids.
 */
class MultiDatasourceCaptureSampleTest {

    @Test
    @DisplayName("Given two registered datasources, when each captures a query, then emitted events preserve datasource identity")
    void shouldCaptureTwoDatasourcesWithDistinctIds() throws Exception {
        var usersSource = mock(DataSource.class);
        var ordersSource = mock(DataSource.class);

        var usersConn = mock(java.sql.Connection.class);
        var ordersConn = mock(java.sql.Connection.class);
        var usersStmt = mock(PreparedStatement.class);
        var ordersStmt = mock(PreparedStatement.class);
        when(usersConn.prepareStatement("UPDATE users SET active = true")).thenReturn(usersStmt);
        when(ordersConn.prepareStatement("UPDATE orders SET synced = true")).thenReturn(ordersStmt);
        when(usersStmt.executeUpdate()).thenReturn(1);
        when(ordersStmt.executeUpdate()).thenReturn(2);

        var emittedProtoEvents = new CopyOnWriteArrayList<MockedQuery>();

        try (var usersBinding = JdbcAgentRegistry.register(
                     usersSource, "users-primary",
                     event -> {},
                     MockedQueryEventProducer.fromConsumer(emittedProtoEvents::add),
                     AsyncDispatchConfig.defaults());
             var ordersBinding = JdbcAgentRegistry.register(
                     ordersSource, "orders-replica",
                     event -> {},
                     MockedQueryEventProducer.fromConsumer(emittedProtoEvents::add),
                     AsyncDispatchConfig.defaults())) {

            var wrappedUsers  = JdbcAgentRegistry.wrapIfRegistered(usersSource, usersConn);
            var wrappedOrders = JdbcAgentRegistry.wrapIfRegistered(ordersSource, ordersConn);

            wrappedUsers.prepareStatement("UPDATE users SET active = true").executeUpdate();
            wrappedOrders.prepareStatement("UPDATE orders SET synced = true").executeUpdate();

            // Dispatch is async; wait briefly for both events.
            for (var i = 0; i < 20 && emittedProtoEvents.size() < 2; i++) {
                Thread.sleep(50L);
            }

            assertEquals(2, emittedProtoEvents.size());
            assertTrue(emittedProtoEvents.stream().anyMatch(e -> "users-primary".equals(e.getDatasourceId())));
            assertTrue(emittedProtoEvents.stream().anyMatch(e -> "orders-replica".equals(e.getDatasourceId())));

        } finally {
            JdbcAgentRegistry.unregister(usersSource);
            JdbcAgentRegistry.unregister(ordersSource);
        }
    }
}
