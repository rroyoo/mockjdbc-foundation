package io.github.rroyoo.mockjdbc.proxy;

import io.github.rroyoo.mockjdbc.mock.MockedQuery;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;

@FunctionalInterface
public interface MockedQueryEventProducer {

    void send(MockedQuery event);

    /**
     * Sends the event and returns a future completed when delivery finishes (exceptionally on
     * failure). The default completes immediately after {@link #send}; asynchronous transports
     * should override it so delivery failures are reported.
     */
    default CompletionStage<Void> sendAsync(MockedQuery event) {
        try {
            send(event);
            return CompletableFuture.completedFuture(null);
        } catch (RuntimeException e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    static MockedQueryEventProducer fromConsumer(Consumer<MockedQuery> consumer) {
        Objects.requireNonNull(consumer, "consumer is required");
        return consumer::accept;
    }
}

