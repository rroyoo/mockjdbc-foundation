package io.github.rroyoo.mockjdbc.wiremock;

import io.github.rroyoo.mockjdbc.mock.MockedQuery;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.errors.WakeupException;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.StringDeserializer;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;

public final class KafkaMappingConsumer implements AutoCloseable {

    private final Consumer<String, byte[]> consumer;
    private final WireMockMappingRegistrar registrar;
    private final String topic;
    private final Duration pollTimeout;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private Thread consumerThread;

    public KafkaMappingConsumer(KafkaMappingConsumerConfig config) {
        Objects.requireNonNull(config, "config is required");

        var mapper = new MockedQueryStubMapper(config.datasourceAllowlist(), config.sqlDenyPrefixes());
        this.registrar = new WireMockMappingRegistrar(config.wireMockServer(), mapper);
        this.consumer = new KafkaConsumer<>(consumerProperties(config));
        this.topic = config.topic();
        this.pollTimeout = config.pollTimeout();
    }

    KafkaMappingConsumer(Consumer<String, byte[]> consumer,
                         WireMockMappingRegistrar registrar,
                         String topic,
                         Duration pollTimeout) {
        this.consumer = Objects.requireNonNull(consumer, "consumer is required");
        this.registrar = Objects.requireNonNull(registrar, "registrar is required");
        this.topic = Objects.requireNonNull(topic, "topic is required");
        this.pollTimeout = Objects.requireNonNull(pollTimeout, "pollTimeout is required");
    }

    public synchronized void start() {
        if (running.get()) {
            return;
        }

        running.set(true);
        consumer.subscribe(List.of(topic));
        consumerThread = new Thread(this::pollLoop, "mockjdbc-wiremock-kafka-consumer");
        consumerThread.setDaemon(true);
        consumerThread.start();
    }

    private void pollLoop() {
        try {
            while (running.get()) {
                var records = consumer.poll(pollTimeout);
                for (ConsumerRecord<String, byte[]> record : records) {
                    processRecord(record);
                }
            }
        } catch (WakeupException wakeupException) {
            if (running.get()) {
                throw wakeupException;
            }
        } finally {
            consumer.close();
        }
    }

    private void processRecord(ConsumerRecord<String, byte[]> record) {
        try {
            var event = MockedQuery.parseFrom(record.value());
            registrar.upsert(event);
        } catch (Exception ignored) {
            // Keep loop alive and skip malformed records.
        }
    }

    @Override
    public synchronized void close() {
        if (!running.get()) {
            return;
        }

        running.set(false);
        consumer.wakeup();
        if (consumerThread != null) {
            try {
                consumerThread.join(2000);
            } catch (InterruptedException interruptedException) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private static Properties consumerProperties(KafkaMappingConsumerConfig config) {
        var properties = new Properties();
        properties.setProperty(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, config.bootstrapServers());
        properties.setProperty(ConsumerConfig.GROUP_ID_CONFIG, config.groupId());
        properties.setProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        properties.setProperty(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "true");
        properties.setProperty(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        properties.setProperty(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class.getName());
        return properties;
    }
}
