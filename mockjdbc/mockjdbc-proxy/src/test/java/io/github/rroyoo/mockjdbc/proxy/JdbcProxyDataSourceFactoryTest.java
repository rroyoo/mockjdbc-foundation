package io.github.rroyoo.mockjdbc.proxy;

import io.github.rroyoo.mockjdbc.mock.MockedQuery;
import net.ttddyy.dsproxy.ExecutionInfo;
import net.ttddyy.dsproxy.QueryInfo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class JdbcProxyDataSourceFactoryTest {

    @Test
    @DisplayName("Given explicit datasource id, when wrapping datasource, then listener emits events using that datasource id")
    void shouldPreserveDatasourceIdThroughFactoryBinding() throws Exception {
        var target = mock(DataSource.class);
        var localEvents = new CopyOnWriteArrayList<JdbcQueryInterceptedEvent>();
        var protoEvent = new AtomicReference<MockedQuery>();
        var latch = new CountDownLatch(1);

        try (var binding = JdbcProxyDataSourceFactory.wrap(
                target,
                "orders-replica",
                localEvents::add,
                event -> {
                    protoEvent.set(event);
                    latch.countDown();
                },
                AsyncDispatchConfig.defaults()
        )) {
            assertNotNull(binding.dataSource());
            assertNotNull(binding.listener());

            var executionInfo = new ExecutionInfo();
            executionInfo.setSuccess(true);
            executionInfo.setResult(2);

            binding.listener().afterQuery(executionInfo, List.of(new QueryInfo("UPDATE orders SET synced = true")));

            assertEquals(1, localEvents.size());
            assertEquals("orders-replica", localEvents.get(0).datasourceId());
            assertTrue(latch.await(2, TimeUnit.SECONDS));
            assertNotNull(protoEvent.get());
            assertEquals("orders-replica", protoEvent.get().getDatasourceId());
        }
    }
}
