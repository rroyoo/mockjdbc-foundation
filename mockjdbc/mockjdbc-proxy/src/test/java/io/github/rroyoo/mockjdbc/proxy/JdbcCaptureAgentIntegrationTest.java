package io.github.rroyoo.mockjdbc.proxy;

import net.bytebuddy.agent.ByteBuddyAgent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import sample.agent.StubDataSource;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JdbcCaptureAgentIntegrationTest {

    @Test
    @DisplayName("Given installed agent and registered DataSource, when getConnection is called, then a capturing connection is returned without manual wrapping")
    void shouldReturnCapturingConnectionFromInstrumentedDataSource() throws Exception {
        JdbcCaptureAgent.install(ByteBuddyAgent.install());

        var rawConnection = mock(Connection.class);
        var statement = mock(Statement.class);
        when(rawConnection.createStatement()).thenReturn(statement);
        when(statement.executeUpdate("DELETE FROM users")).thenReturn(3);

        var registeredDataSource = new StubDataSource(rawConnection);
        var unregisteredDataSource = new StubDataSource(rawConnection);

        try (var registration = JdbcAgentRegistry.register(registeredDataSource, "agent-ds",
                event -> {}, MockedQueryEventProducer.fromConsumer(q -> {}), AsyncDispatchConfig.defaults())) {

            var connection = registeredDataSource.getConnection();

            assertTrue(Proxy.isProxyClass(connection.getClass()));
            assertTrue(Proxy.getInvocationHandler(connection) instanceof CapturingConnectionInvocationHandler);
            assertSame(rawConnection, unregisteredDataSource.getConnection());

            assertEquals(3, connection.createStatement().executeUpdate("DELETE FROM users"));
            assertEquals(1, registration.events().size());
            assertEquals("DELETE FROM users", registration.events().get(0).sql());
        }
        assertNull(JdbcAgentRegistry.lookup(registeredDataSource));
    }
}
