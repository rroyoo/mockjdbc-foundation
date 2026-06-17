package io.github.rroyoo.mockjdbc.proxy;

import io.github.rroyoo.mockjdbc.mock.MockedQuery;

import java.util.Objects;
import java.util.function.Consumer;

@FunctionalInterface
public interface MockedQueryEventProducer {

    void send(MockedQuery event);

    static MockedQueryEventProducer fromConsumer(Consumer<MockedQuery> consumer) {
        Objects.requireNonNull(consumer, "consumer is required");
        return consumer::accept;
    }
}

