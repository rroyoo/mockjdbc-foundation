package io.github.rroyoo.mockjdbc.wiremock;

import com.google.protobuf.InvalidProtocolBufferException;
import io.github.rroyoo.mockjdbc.mock.MockedQuery;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.errors.WakeupException;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.StringDeserializer;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;

public final class KafkaMappingConsumer implements AutoCloseable {

    private static final System.Logger LOGGER = System.getLogger(KafkaMappingConsumer.class.getName());

    private final Consumer<String, byte[]> consumer;
    private final WireMockMappingRegistrar registrar;
    private final String topic;
    private final Duration pollTimeout;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private Thread consumerThread;

    public KafkaMappingConsumer(KafkaMappingConsumerConfig config) {
        Objects.requireNonNull(config, "config is required");

        var mapper = new MockedQueryStubMapper(config.datasourceAllowlist(), config.sqlDenyPrefixes());
        this.registrar = new WireMockMappingRegistrar(config.admin(), mapper);
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

    /**
     * Registration runs on the poll thread, so per-partition order is preserved and the amount of
     * in-flight work is bounded by max.poll.records. Offsets are committed only after every record
     * of a partition prefix has been registered; a failed registration is not committed and the
     * partition is rewound so the record is retried on the next poll.
     */
    private void pollLoop() {
        try {
            while (running.get()) {
                var records = consumer.poll(pollTimeout);
                var toCommit = new HashMap<TopicPartition, OffsetAndMetadata>();
                var failed = false;
                for (TopicPartition partition : records.partitions()) {
                    for (ConsumerRecord<String, byte[]> record : records.records(partition)) {
                        try {
                            process(record);
                            toCommit.put(partition, new OffsetAndMetadata(record.offset() + 1));
                        } catch (RuntimeException registrationFailure) {
                            LOGGER.log(System.Logger.Level.ERROR,
                                    "Registration failed at " + partition + "@" + record.offset() + "; will retry",
                                    registrationFailure);
                            consumer.seek(partition, record.offset());
                            failed = true;
                            break;
                        }
                    }
                }
                if (!toCommit.isEmpty()) {
                    consumer.commitSync(toCommit);
                }
                if (failed) {
                    backoff();
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

    private void process(ConsumerRecord<String, byte[]> record) {
        MockedQuery event;
        try {
            event = MockedQuery.parseFrom(record.value());
        } catch (InvalidProtocolBufferException malformed) {
            // Poison pill: retrying can never succeed, so log and skip it (it is then committed).
            LOGGER.log(System.Logger.Level.WARNING,
                    "Skipping malformed record " + record.topic() + "-" + record.partition() + "@" + record.offset(),
                    malformed);
            return;
        }
        registrar.upsert(event);
    }

    private void backoff() {
        try {
            Thread.sleep(pollTimeout.toMillis());
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            running.set(false);
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
        properties.setProperty(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
        // Batch tuning: fetch up to 500 records per poll, wait at most 500 ms or 64 KB.
        properties.setProperty(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, "500");
        properties.setProperty(ConsumerConfig.FETCH_MIN_BYTES_CONFIG, "65536");
        properties.setProperty(ConsumerConfig.FETCH_MAX_WAIT_MS_CONFIG, "500");
        properties.setProperty(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        properties.setProperty(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class.getName());
        return properties;
    }
}
