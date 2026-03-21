package io.github.rroyoo.mockjdbc.wiremock;

import com.github.tomakehurst.wiremock.extension.Extension;

import java.util.Objects;

/**
 * WireMock extension wrapper that manages the Kafka bridge lifecycle.
 */
public final class WireMockKafkaBridgeExtension implements Extension {

    private final String name;
    private final KafkaMappingConsumer consumer;

    public WireMockKafkaBridgeExtension(KafkaMappingConsumerConfig config) {
        this("wiremock-kafka-bridge", new KafkaMappingConsumer(config));
    }

    public WireMockKafkaBridgeExtension(KafkaMappingConsumer consumer) {
        this("wiremock-kafka-bridge", consumer);
    }

    public WireMockKafkaBridgeExtension(String name, KafkaMappingConsumer consumer) {
        this.name = Objects.requireNonNull(name, "name is required");
        this.consumer = Objects.requireNonNull(consumer, "consumer is required");
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public void start() {
        consumer.start();
    }

    @Override
    public void stop() {
        consumer.close();
    }
}

