package io.github.rroyoo.mockjdbc.proxy;

import io.github.rroyoo.mockjdbc.mock.MockedQuery;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;

import java.util.Objects;
import java.util.Properties;
import java.util.function.Function;

public final class KafkaMockedQueryEventProducer implements MockedQueryEventProducer, AutoCloseable {

    private final Producer<String, byte[]> producer;
    private final String topic;
    private final Function<MockedQuery, String> keyResolver;

    public KafkaMockedQueryEventProducer(Producer<String, byte[]> producer,
                                         String topic,
                                         Function<MockedQuery, String> keyResolver) {
        this.producer = Objects.requireNonNull(producer, "producer is required");
        this.topic = Objects.requireNonNull(topic, "topic is required");
        this.keyResolver = Objects.requireNonNull(keyResolver, "keyResolver is required");
    }

    public static KafkaMockedQueryEventProducer create(Properties properties,
                                                       String topic,
                                                       Function<MockedQuery, String> keyResolver) {
        var kafkaProperties = new Properties();
        kafkaProperties.putAll(Objects.requireNonNull(properties, "properties is required"));
        kafkaProperties.putIfAbsent("key.serializer", StringSerializer.class.getName());
        kafkaProperties.putIfAbsent("value.serializer", "org.apache.kafka.common.serialization.ByteArraySerializer");
        return new KafkaMockedQueryEventProducer(new KafkaProducer<>(kafkaProperties), topic, keyResolver);
    }

    public static Function<MockedQuery, String> datasourceKeyResolver() {
        return event -> {
            if (event == null) {
                return "unknown-datasource";
            }
            var datasourceId = event.getDatasourceId();
            if (datasourceId == null || datasourceId.isBlank()) {
                return "unknown-datasource";
            }
            return datasourceId;
        };
    }

    @Override
    public void send(MockedQuery event) throws Exception {
        var key = keyResolver.apply(event);
        var payload = event == null ? new byte[0] : event.toByteArray();
        producer.send(new ProducerRecord<>(topic, key, payload)).get();
    }

    @Override
    public void close() {
        producer.close();
    }
}

