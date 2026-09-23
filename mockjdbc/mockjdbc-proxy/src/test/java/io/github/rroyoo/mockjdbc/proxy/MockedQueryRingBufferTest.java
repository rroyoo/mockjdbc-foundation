package io.github.rroyoo.mockjdbc.proxy;

import io.github.rroyoo.mockjdbc.mock.MockedQuery;
import io.github.rroyoo.mockjdbc.mock.PlainStatement;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Deterministic single-threaded correctness tests plus a concurrent stress test for the
 * lock-free MPMC {@link MockedQueryRingBuffer}.
 */
class MockedQueryRingBufferTest {

    private static MockedQuery query(String sql) {
        return MockedQuery.newBuilder().setSimpleStatement(PlainStatement.newBuilder().setSql(sql).build()).build();
    }

    @Test
    @DisplayName("Given a requested capacity of 1, when constructed, then the effective capacity is rounded up to 2")
    void shouldRoundUpCapacityToAtLeastTwo() {
        var ringBuffer = new MockedQueryRingBuffer(1);
        assertEquals(2, ringBuffer.capacity());
    }

    @Test
    @DisplayName("Given a requested capacity of 5, when constructed, then the effective capacity is rounded up to the next power of two")
    void shouldRoundUpNonPowerOfTwoCapacity() {
        var ringBuffer = new MockedQueryRingBuffer(5);
        assertEquals(8, ringBuffer.capacity());
    }

    @Test
    @DisplayName("Given an empty ring buffer, when polling, then it returns null")
    void shouldReturnNullWhenEmpty() {
        var ringBuffer = new MockedQueryRingBuffer(2);
        assertNull(ringBuffer.tryPoll());
    }

    @Test
    @DisplayName("Given a ring buffer at capacity, when offering one more event, then it is rejected")
    void shouldRejectOfferWhenFull() {
        var ringBuffer = new MockedQueryRingBuffer(2);
        assertTrue(ringBuffer.tryOffer(query("a")));
        assertTrue(ringBuffer.tryOffer(query("b")));
        assertFalse(ringBuffer.tryOffer(query("c")));
        assertEquals(2, ringBuffer.size());
    }

    @Test
    @DisplayName("Given events offered in order, when polled, then they are returned in FIFO order")
    void shouldPreserveFifoOrder() {
        var ringBuffer = new MockedQueryRingBuffer(4);
        ringBuffer.tryOffer(query("a"));
        ringBuffer.tryOffer(query("b"));
        ringBuffer.tryOffer(query("c"));

        assertEquals("a", ringBuffer.tryPoll().getSimpleStatement().getSql());
        assertEquals("b", ringBuffer.tryPoll().getSimpleStatement().getSql());
        assertEquals("c", ringBuffer.tryPoll().getSimpleStatement().getSql());
        assertNull(ringBuffer.tryPoll());
    }

    @Test
    @DisplayName("Given a slot freed by a poll, when offering again, then the buffer reuses the slot correctly across many wraparounds")
    void shouldReuseSlotsAcrossManyWraparounds() {
        var ringBuffer = new MockedQueryRingBuffer(2);
        for (var i = 0; i < 10_000; i++) {
            assertTrue(ringBuffer.tryOffer(query("event-" + i)));
            var polled = ringBuffer.tryPoll();
            assertEquals("event-" + i, polled.getSimpleStatement().getSql());
        }
        assertEquals(0, ringBuffer.size());
    }

    @Test
    @DisplayName("Given multiple producer threads publishing concurrently, when a single consumer drains, then every published event is delivered exactly once without loss or duplication")
    void shouldNotLoseOrDuplicateEventsUnderConcurrentProducers() throws Exception {
        var ringBuffer = new MockedQueryRingBuffer(64);
        var producerCount = 8;
        var eventsPerProducer = 2_000;
        var totalEvents = producerCount * eventsPerProducer;

        var received = new ConcurrentLinkedQueue<String>();
        var stopConsumer = new java.util.concurrent.atomic.AtomicBoolean(false);
        var consumedCount = new AtomicInteger(0);

        ExecutorService pool = Executors.newFixedThreadPool(producerCount + 1);
        var consumerDone = new CountDownLatch(1);

        pool.submit(() -> {
            while (!stopConsumer.get() || ringBuffer.size() > 0) {
                var event = ringBuffer.tryPoll();
                if (event != null) {
                    received.add(event.getSimpleStatement().getSql());
                    consumedCount.incrementAndGet();
                }
            }
            consumerDone.countDown();
        });

        var startLatch = new CountDownLatch(1);
        var producersDone = new CountDownLatch(producerCount);
        for (var p = 0; p < producerCount; p++) {
            var producerId = p;
            pool.submit(() -> {
                try {
                    startLatch.await();
                    for (var i = 0; i < eventsPerProducer; i++) {
                        // Retry on transient contention; the buffer is generously sized relative
                        // to the producer/consumer speed ratio so offers should rarely fail.
                        while (!ringBuffer.tryOffer(query("p" + producerId + "-e" + i))) {
                            Thread.onSpinWait();
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    producersDone.countDown();
                }
            });
        }

        startLatch.countDown();
        assertTrue(producersDone.await(30, TimeUnit.SECONDS));
        stopConsumer.set(true);
        assertTrue(consumerDone.await(30, TimeUnit.SECONDS));
        pool.shutdownNow();

        assertEquals(totalEvents, consumedCount.get());
        assertEquals(totalEvents, received.size());
        assertEquals(totalEvents, List.copyOf(received).stream().distinct().count());
    }
}
