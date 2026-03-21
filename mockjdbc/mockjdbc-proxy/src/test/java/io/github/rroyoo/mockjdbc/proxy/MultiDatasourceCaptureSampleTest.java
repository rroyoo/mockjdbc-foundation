package io.github.rroyoo.mockjdbc.proxy;

import io.github.rroyoo.mockjdbc.mock.MockedQuery;
import net.ttddyy.dsproxy.ExecutionInfo;
import net.ttddyy.dsproxy.QueryInfo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class MultiDatasourceCaptureSampleTest {

    @Test
    @DisplayName("Given two proxied datasources, when each captures a query, then emitted events preserve datasource identity")
    void shouldCaptureTwoDatasourcesWithDistinctIds() throws Exception {
        var usersSource = mock(DataSource.class);
        var ordersSource = mock(DataSource.class);

        var emittedProtoEvents = new CopyOnWriteArrayList<MockedQuery>();

        try (var usersBinding = JdbcProxyDataSourceFactory.wrap(
                usersSource,
                "users-primary",
                event -> {},
                emittedProtoEvents::add,
                AsyncDispatchConfig.defaults()
        );
             var ordersBinding = JdbcProxyDataSourceFactory.wrap(
                     ordersSource,
                     "orders-replica",
                     event -> {},
                     emittedProtoEvents::add,
                     AsyncDispatchConfig.defaults()
             )) {

            var usersExecution = new ExecutionInfo();
            usersExecution.setSuccess(true);
            usersExecution.setResult(1);
            usersBinding.listener().afterQuery(usersExecution, List.of(new QueryInfo("UPDATE users SET active = true")));

            var ordersExecution = new ExecutionInfo();
            ordersExecution.setSuccess(true);
            ordersExecution.setResult(2);
            ordersBinding.listener().afterQuery(ordersExecution, List.of(new QueryInfo("UPDATE orders SET synced = true")));

            // Dispatch is async; wait briefly for both events.
            for (var i = 0; i < 20 && emittedProtoEvents.size() < 2; i++) {
                Thread.sleep(50L);
            }

            assertEquals(2, emittedProtoEvents.size());
            assertTrue(emittedProtoEvents.stream().anyMatch(event -> "users-primary".equals(event.getDatasourceId())));
            assertTrue(emittedProtoEvents.stream().anyMatch(event -> "orders-replica".equals(event.getDatasourceId())));
        }
    }
}

