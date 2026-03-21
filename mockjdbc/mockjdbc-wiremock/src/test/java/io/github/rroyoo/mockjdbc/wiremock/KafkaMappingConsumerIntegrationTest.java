package io.github.rroyoo.mockjdbc.wiremock;

import com.github.tomakehurst.wiremock.WireMockServer;
import io.github.rroyoo.mockjdbc.mock.MockedQuery;
import io.github.rroyoo.mockjdbc.mock.PlainStatement;
import io.github.rroyoo.mockjdbc.mock.QueryExecutionStatus;
import io.github.rroyoo.mockjdbc.mock.SerializedResultSet;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers(disabledWithoutDocker = true)
class KafkaMappingConsumerIntegrationTest {

    @Container
    static final KafkaContainer KAFKA = new KafkaContainer(DockerImageName.parse("apache/kafka-native:3.8.1"));

    @Test
    @DisplayName("Given Kafka MockedQuery event, consumer registers WireMock stub and endpoint returns protobuf payload")
    void shouldBridgeKafkaToWireMock() throws Exception {
        var wireMockServer = new WireMockServer(0);
        wireMockServer.start();

        var topic = "mockjdbc.query.events.bridge";

        var config = KafkaMappingConsumerConfig.defaults(
                KAFKA.getBootstrapServers(),
                topic,
                "mockjdbc-wiremock-bridge-it",
                wireMockServer
        );

        try (var consumer = new KafkaMappingConsumer(config)) {
            consumer.start();

            var resultSet = SerializedResultSet.newBuilder().build();
            var event = MockedQuery.newBuilder()
                    .setDatasourceId("users-primary")
                    .setStatus(QueryExecutionStatus.QUERY_EXECUTION_STATUS_SUCCESS)
                    .setSimpleStatement(PlainStatement.newBuilder().setSql("SELECT 42;").build())
                    .setResultSet(resultSet)
                    .build();

            var producerProps = new Properties();
            producerProps.setProperty("bootstrap.servers", KAFKA.getBootstrapServers());
            producerProps.setProperty("acks", "all");
            producerProps.setProperty("key.serializer", StringSerializer.class.getName());
            producerProps.setProperty("value.serializer", ByteArraySerializer.class.getName());

            try (var producer = new KafkaProducer<String, byte[]>(producerProps)) {
                producer.send(new ProducerRecord<>(topic, "users-primary", event.toByteArray())).get();
            }

            var client = HttpClient.newHttpClient();
            HttpResponse<byte[]> response = null;

            for (var i = 0; i < 20; i++) {
                var request = HttpRequest.newBuilder()
                        .uri(URI.create("http://localhost:" + wireMockServer.port() + "/mockjdbc/query"))
                        .timeout(Duration.ofSeconds(1))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString("{\"sql\":\"select 42\",\"datasourceId\":\"users-primary\"}"))
                        .build();

                response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
                if (response.statusCode() == 200) {
                    break;
                }
                Thread.sleep(100L);
            }

            assertEquals(200, response.statusCode());
            assertTrue(response.body().length >= 0);
        } finally {
            wireMockServer.stop();
        }
    }
}

