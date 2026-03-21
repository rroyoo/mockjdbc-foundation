package io.github.rroyoo.mockjdbc.proxy;

import io.github.rroyoo.mockjdbc.mock.MockedQuery;
import io.github.rroyoo.mockjdbc.mock.PlainStatement;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@Testcontainers(disabledWithoutDocker = true)
class KafkaMockedQueryEventProducerIntegrationTest {

    @Container
    static final KafkaContainer KAFKA = new KafkaContainer(DockerImageName.parse("apache/kafka-native:3.8.1"));

    @Test
    @DisplayName("Given running Kafka, when producer sends MockedQuery, then consumer receives keyed protobuf payload")
    void shouldPublishAndConsumeMockedQueryEvent() throws Exception {
        var topic = "mockjdbc-query-events-it";

        var producerProperties = new Properties();
        producerProperties.setProperty("bootstrap.servers", KAFKA.getBootstrapServers());
        producerProperties.setProperty("acks", "all");
        producerProperties.setProperty("retries", "3");

        try (var producer = KafkaMockedQueryEventProducer.create(
                producerProperties,
                topic,
                KafkaMockedQueryEventProducer.datasourceKeyResolver()
        )) {
            var event = MockedQuery.newBuilder()
                    .setDatasourceId("users-primary")
                    .setSimpleStatement(PlainStatement.newBuilder().setSql("SELECT 42").build())
                    .build();

            producer.send(event);
        }

        var consumerProperties = new Properties();
        consumerProperties.setProperty(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        consumerProperties.setProperty(ConsumerConfig.GROUP_ID_CONFIG, "mockjdbc-proxy-it-" + UUID.randomUUID());
        consumerProperties.setProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        try (var consumer = new KafkaConsumer<String, byte[]>(
                consumerProperties,
                new StringDeserializer(),
                new ByteArrayDeserializer()
        )) {
            consumer.subscribe(List.of(topic));
            ConsumerRecord<String, byte[]> found = null;

            for (var i = 0; i < 20 && found == null; i++) {
                var records = consumer.poll(Duration.ofMillis(250));
                if (!records.isEmpty()) {
                    found = records.iterator().next();
                }
            }

            assertNotNull(found);
            assertEquals("users-primary", found.key());
            assertFalse(found.value().length == 0);

            var decoded = MockedQuery.parseFrom(found.value());
            assertEquals("users-primary", decoded.getDatasourceId());
            assertEquals("SELECT 42", decoded.getSimpleStatement().getSql());
        }
    }
}

