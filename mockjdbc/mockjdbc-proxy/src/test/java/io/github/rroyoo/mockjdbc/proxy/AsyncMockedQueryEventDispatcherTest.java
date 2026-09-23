package io.github.rroyoo.mockjdbc.proxy;

import io.github.rroyoo.mockjdbc.mock.MockedQuery;
import io.github.rroyoo.mockjdbc.mock.PlainStatement;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
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
    @DisplayName("Given a full ring buffer with drop-oldest strategy, when overflowing, then the oldest queued event is evicted and newer ones are delivered")
    void shouldDropOldestOnOverflow() throws Exception {
        var received = new CopyOnWriteArrayList<MockedQuery>();
        var gate = new java.util.concurrent.Semaphore(0);
        var producer = (MockedQueryEventProducer) event -> {
            received.add(event);
            // Block after the first delivery so subsequent publishes accumulate in the ring
            // buffer deterministically, without relying on thread-scheduling timing races.
            if (received.size() == 1) {
                gate.acquireUninterruptibly();
            }
        };

        var config = new AsyncDispatchConfig(2, 1, AsyncDispatchConfig.OverflowStrategy.DROP_OLDEST, 256);
        try (var dispatcher = new AsyncMockedQueryEventDispatcher(producer, config)) {
            dispatcher.publish(query("first"));
            awaitReceivedSize(received, 1);

            dispatcher.publish(query("second")); // fills slot 1
            dispatcher.publish(query("third"));  // fills slot 2
            dispatcher.publish(query("fourth")); // overflow: evicts "second"

            gate.release();

            awaitReceivedSize(received, 3);
            var deliveredSql = received.stream().map(q -> q.getSimpleStatement().getSql()).toList();
            assertEquals(List.of("first", "third", "fourth"), deliveredSql);
            assertEquals(1, dispatcher.stats().dropped());
        }
    }

    @Test
    @DisplayName("Given a full ring buffer with drop-newest strategy, when overflowing, then the incoming event is rejected and previously queued events are delivered")
    void shouldDropNewestOnOverflow() throws Exception {
        var received = new CopyOnWriteArrayList<MockedQuery>();
        var gate = new java.util.concurrent.Semaphore(0);
        var producer = (MockedQueryEventProducer) event -> {
            received.add(event);
            if (received.size() == 1) {
                gate.acquireUninterruptibly();
            }
        };

        var config = new AsyncDispatchConfig(2, 1, AsyncDispatchConfig.OverflowStrategy.DROP_NEWEST, 256);
        try (var dispatcher = new AsyncMockedQueryEventDispatcher(producer, config)) {
            dispatcher.publish(query("first"));
            awaitReceivedSize(received, 1);

            dispatcher.publish(query("second")); // fills slot 1
            dispatcher.publish(query("third"));  // fills slot 2
            dispatcher.publish(query("fourth")); // overflow: rejected, buffer unchanged

            gate.release();

            awaitReceivedSize(received, 3);
            var deliveredSql = received.stream().map(q -> q.getSimpleStatement().getSql()).toList();
            assertEquals(List.of("first", "second", "third"), deliveredSql);
            assertEquals(1, dispatcher.stats().dropped());
        }
    }

    private static void awaitReceivedSize(CopyOnWriteArrayList<MockedQuery> received, int expectedSize)
            throws InterruptedException {
        var deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (received.size() < expectedSize && System.nanoTime() < deadline) {
            Thread.sleep(10);
        }
        assertEquals(expectedSize, received.size());
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

