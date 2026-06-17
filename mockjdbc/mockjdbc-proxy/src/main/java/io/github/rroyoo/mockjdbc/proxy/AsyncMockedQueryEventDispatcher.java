package io.github.rroyoo.mockjdbc.proxy;

import io.github.rroyoo.mockjdbc.mock.MockedQuery;

import java.util.Objects;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

final class AsyncMockedQueryEventDispatcher implements AutoCloseable {

    private final MockedQueryEventProducer producer;
    private final BlockingDeque<MockedQuery> ringBuffer;
    private final AtomicBoolean running = new AtomicBoolean(true);
    private final java.util.concurrent.ExecutorService senderPool;
    private final AsyncDispatchConfig.OverflowStrategy overflowStrategy;
    private final AtomicLong droppedCount = new AtomicLong(0);
    private final AtomicLong failedSendCount = new AtomicLong(0);
    private final AtomicLong offeredCount = new AtomicLong(0);
    private final AtomicLong sentCount = new AtomicLong(0);

    AsyncMockedQueryEventDispatcher(MockedQueryEventProducer producer, AsyncDispatchConfig config) {
        this.producer = Objects.requireNonNull(producer, "producer is required");
        this.ringBuffer = new LinkedBlockingDeque<>(config.ringBufferCapacity());
        this.overflowStrategy = config.overflowStrategy();
        this.senderPool = Executors.newFixedThreadPool(config.senderThreads(), senderThreadFactory());

        for (var i = 0; i < config.senderThreads(); i++) {
            senderPool.submit(this::sendLoop);
        }
    }

    boolean publish(MockedQuery event) {
        if (!running.get()) {
            return false;
        }

        offeredCount.incrementAndGet();

        if (ringBuffer.offerLast(event)) {
            return true;
        }

        if (overflowStrategy == AsyncDispatchConfig.OverflowStrategy.DROP_OLDEST) {
            droppedCount.incrementAndGet();
            ringBuffer.pollFirst();
            return ringBuffer.offerLast(event);
        }

        droppedCount.incrementAndGet();
        return false;
    }

    AsyncDispatchStats stats() {
        return new AsyncDispatchStats(
                offeredCount.get(),
                sentCount.get(),
                droppedCount.get(),
                failedSendCount.get(),
                ringBuffer.size()
        );
    }

    @Override
    public void close() {
        running.set(false);
        senderPool.shutdownNow();
        try {
            senderPool.awaitTermination(2, TimeUnit.SECONDS);
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
        }
    }

    private void sendLoop() {
        while (running.get() || !ringBuffer.isEmpty()) {
            try {
                var event = ringBuffer.pollFirst(100, TimeUnit.MILLISECONDS);
                if (event == null) {
                    continue;
                }
                producer.send(event);
                sentCount.incrementAndGet();
            } catch (InterruptedException interruptedException) {
                Thread.currentThread().interrupt();
                return;
            } catch (RuntimeException ignored) {
                failedSendCount.incrementAndGet();
                // Keep sender loop alive; failed events are intentionally dropped.
            }
        }
    }

    private static ThreadFactory senderThreadFactory() {
        var counter = new AtomicInteger(1);
        return runnable -> {
            var thread = new Thread(runnable, "mockjdbc-proxy-sender-" + counter.getAndIncrement());
            thread.setDaemon(true);
            return thread;
        };
    }
}

