package io.github.rroyoo.mockjdbc.proxy;

import io.github.rroyoo.mockjdbc.mock.MockedQuery;
import io.github.rroyoo.mockjdbc.mock.PlainStatement;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Tiny manual benchmark harness for async dispatcher tuning.
 */
public final class AsyncDispatcherBenchmarkRunner {

    private AsyncDispatcherBenchmarkRunner() {
    }

    public static void main(String[] args) throws Exception {
        var produced = new AtomicLong(0);
        var producer = (MockedQueryEventProducer) event -> {
            Thread.sleep(1L);
            produced.incrementAndGet();
        };

        var config = new AsyncDispatchConfig(4096, 4, AsyncDispatchConfig.OverflowStrategy.DROP_OLDEST, 512);

        try (var dispatcher = new AsyncMockedQueryEventDispatcher(producer, config)) {
            var start = System.nanoTime();
            for (var i = 0; i < 100_000; i++) {
                dispatcher.publish(query(i));
            }
            TimeUnit.SECONDS.sleep(2);
            var elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
            var stats = dispatcher.stats();

            System.out.println("elapsedMs=" + elapsedMillis);
            System.out.println("offered=" + stats.offered());
            System.out.println("sent=" + stats.sent());
            System.out.println("dropped=" + stats.dropped());
            System.out.println("failed=" + stats.failed());
            System.out.println("queued=" + stats.queued());
            System.out.println("producedCount=" + produced.get());
        }
    }

    private static MockedQuery query(long id) {
        return MockedQuery.newBuilder()
                .setSimpleStatement(PlainStatement.newBuilder().setSql("SELECT " + id).build())
                .build();
    }
}

