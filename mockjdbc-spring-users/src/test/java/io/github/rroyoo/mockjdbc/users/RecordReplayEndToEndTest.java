package io.github.rroyoo.mockjdbc.users;

import io.github.rroyoo.mockjdbc.proxy.AsyncDispatchConfig;
import io.github.rroyoo.mockjdbc.proxy.JdbcAgentRegistry;
import io.github.rroyoo.mockjdbc.proxy.KafkaMockedQueryEventProducer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.ComposeContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.File;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.DriverManager;
import java.time.Duration;
import java.util.Locale;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers(disabledWithoutDocker = true)
class RecordReplayEndToEndTest {

    private static final int KAFKA_PORT = 9094;
    private static final int WIREMOCK_PORT = 8080;
    private static final String TOPIC = "mockjdbc.query.events";
    private static final String DATASOURCE_ID = "record-replay-e2e";
    private static final String SQL = "SELECT ? AS captured_value";

    @Container
    private static final ComposeContainer MOCK_BACKEND =
            new ComposeContainer(new File("docker-compose.yml"))
                    .withLocalCompose(true)
                    .withExposedService("kafka-1", KAFKA_PORT)
                    .withExposedService("wiremock-grpc-1", WIREMOCK_PORT,
                            Wait.forHttp("/__admin").forStatusCode(200));

    @Test
    @DisplayName("Given a captured H2 query, when replayed, then Kafka-generated WireMock stub returns its result")
    void shouldReplayCapturedQueryWithoutHandWrittenMapping() throws Exception {
        var kafkaBootstrap = MOCK_BACKEND.getServiceHost("kafka-1", KAFKA_PORT)
                + ":" + MOCK_BACKEND.getServicePort("kafka-1", KAFKA_PORT);
        var wireMockHost = MOCK_BACKEND.getServiceHost("wiremock-grpc-1", WIREMOCK_PORT);
        var wireMockPort = MOCK_BACKEND.getServicePort("wiremock-grpc-1", WIREMOCK_PORT);
        var expectedMappingName = DATASOURCE_ID + "::" + SQL.toLowerCase(Locale.ROOT) + "::p1=4242";

        var dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.h2.Driver");
        dataSource.setUrl("jdbc:h2:mem:record-replay-e2e;DB_CLOSE_DELAY=-1");
        dataSource.setUsername("sa");
        dataSource.setPassword("");

        var kafkaProperties = new Properties();
        kafkaProperties.setProperty("bootstrap.servers", kafkaBootstrap);
        kafkaProperties.setProperty("acks", "all");

        try (var producer = KafkaMockedQueryEventProducer.create(
                     kafkaProperties,
                     TOPIC,
                     KafkaMockedQueryEventProducer.datasourceKeyResolver());
             var registration = JdbcAgentRegistry.register(
                     dataSource,
                     DATASOURCE_ID,
                     event -> { },
                     producer::send,
                     AsyncDispatchConfig.defaults());
             var connection = JdbcAgentRegistry.wrapIfRegistered(dataSource, dataSource.getConnection());
             var statement = connection.prepareStatement(SQL)) {
            statement.setInt(1, 4242);
            try (var resultSet = statement.executeQuery()) {
                assertTrue(resultSet.next());
                assertEquals(4242, resultSet.getInt("captured_value"));
                assertFalse(resultSet.next());
            }
            assertEquals(1, registration.events().size(), "The JDBC proxy should capture the query");
            awaitGeneratedStub(wireMockHost, wireMockPort, expectedMappingName);
        }

        try (var connection = DriverManager.getConnection(
                     "jdbc:mock://" + wireMockHost + ":" + wireMockPort);
             var statement = connection.prepareStatement(SQL)) {
            statement.setInt(1, 4242);
            try (var resultSet = statement.executeQuery()) {
                assertTrue(resultSet.next());
                assertEquals(4242, resultSet.getInt("captured_value"));
                assertFalse(resultSet.next());
            }
        }
    }

    private static void awaitGeneratedStub(String host, int port, String expectedMappingName) throws Exception {
        var client = HttpClient.newHttpClient();
        var uri = URI.create("http://" + host + ":" + port + "/__admin/mappings");
        var deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
        var lastMappings = "";

        while (System.nanoTime() < deadline) {
            var request = HttpRequest.newBuilder(uri)
                    .timeout(Duration.ofSeconds(2))
                    .GET()
                    .build();
            var response = client.send(request, HttpResponse.BodyHandlers.ofString());
            assertEquals(200, response.statusCode(), "WireMock Admin API should be available");
            lastMappings = response.body();
            if (lastMappings.contains(expectedMappingName)) {
                return;
            }
            Thread.sleep(100);
        }

        throw new AssertionError("Kafka bridge did not register generated stub: " + expectedMappingName
                + "; current mappings: " + lastMappings);
    }
}
