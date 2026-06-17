package io.github.rroyoo.mockjdbc.proxy;

import io.github.rroyoo.mockjdbc.mock.MockedQuery;
import io.github.rroyoo.mockjdbc.mock.PlainStatement;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class AsyncMockedQueryEventDispatcherTest {

    @Test
    @DisplayName("Given dispatcher with sender pool, when publishing events, then producer is called asynchronously")
    void shouldSendEventsAsynchronously() throws Exception {
        var received = new CopyOnWriteArrayList<MockedQuery>();
        var latch = new CountDownLatch(2);
        var producer = (MockedQueryEventProducer) event -> {
            received.add(event);
            latch.countDown();
        };

        var config = new AsyncDispatchConfig(16, 2, AsyncDispatchConfig.OverflowStrategy.DROP_OLDEST, 256);
        try (var dispatcher = new AsyncMockedQueryEventDispatcher(producer, config)) {
            dispatcher.publish(query("SELECT 1"));
            dispatcher.publish(query("SELECT 2"));

            assertTrue(latch.await(2, TimeUnit.SECONDS));
            assertEquals(2, received.size());
        }
    }

    @Test
    @DisplayName("Given small ring buffer with drop oldest, when overflowing, then only newest event is eventually delivered")
    void shouldDropOldestOnOverflow() throws Exception {
        var received = new CopyOnWriteArrayList<MockedQuery>();
        var latch = new CountDownLatch(1);
        var producer = (MockedQueryEventProducer) event -> {
            try {
                Thread.sleep(150);
            } catch (InterruptedException interruptedException) {
                Thread.currentThread().interrupt();
                return;
            }
            received.add(event);
            latch.countDown();
        };

        var config = new AsyncDispatchConfig(1, 1, AsyncDispatchConfig.OverflowStrategy.DROP_OLDEST, 256);
        try (var dispatcher = new AsyncMockedQueryEventDispatcher(producer, config)) {
            dispatcher.publish(query("SELECT old"));
            dispatcher.publish(query("SELECT new"));

            assertTrue(latch.await(3, TimeUnit.SECONDS));
            var deliveredSql = received.get(received.size() - 1).getSimpleStatement().getSql();
            assertEquals("SELECT new", deliveredSql);
        }
    }

    @Test
    @DisplayName("Given small ring buffer with drop newest, when overflowing, then oldest enqueued event is preserved")
    void shouldDropNewestOnOverflow() throws Exception {
        var received = new CopyOnWriteArrayList<MockedQuery>();
        var latch = new CountDownLatch(1);
        var producer = (MockedQueryEventProducer) event -> {
            try {
                Thread.sleep(150);
            } catch (InterruptedException interruptedException) {
                Thread.currentThread().interrupt();
                return;
            }
            received.add(event);
            latch.countDown();
        };

        var config = new AsyncDispatchConfig(1, 1, AsyncDispatchConfig.OverflowStrategy.DROP_NEWEST, 256);
        try (var dispatcher = new AsyncMockedQueryEventDispatcher(producer, config)) {
            dispatcher.publish(query("SELECT old"));
            dispatcher.publish(query("SELECT new"));

            assertTrue(latch.await(3, TimeUnit.SECONDS));
            var deliveredSql = received.get(received.size() - 1).getSimpleStatement().getSql();
            assertEquals("SELECT old", deliveredSql);
        }
    }

    @Test
    @DisplayName("Given a closed dispatcher, when publish is called, then it returns false")
    void shouldRejectPublishAfterClose() {
        var producer = (MockedQueryEventProducer) event -> {
        };
        var config = new AsyncDispatchConfig(4, 1, AsyncDispatchConfig.OverflowStrategy.DROP_OLDEST, 64);

        var dispatcher = new AsyncMockedQueryEventDispatcher(producer, config);
        dispatcher.close();

        assertFalse(dispatcher.publish(query("SELECT 1")));
    }

    private static MockedQuery query(String sql) {
        return MockedQuery.newBuilder()
                .setSimpleStatement(PlainStatement.newBuilder().setSql(sql).build())
                .build();
    }
}

