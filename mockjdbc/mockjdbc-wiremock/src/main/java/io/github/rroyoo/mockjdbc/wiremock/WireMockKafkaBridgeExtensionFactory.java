package io.github.rroyoo.mockjdbc.wiremock;

import com.github.tomakehurst.wiremock.extension.Extension;
import com.github.tomakehurst.wiremock.extension.ExtensionFactory;
import com.github.tomakehurst.wiremock.extension.WireMockServices;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * WireMock {@link ExtensionFactory} that bootstraps the Kafka bridge extension from environment variables.
 *
 * <p>Activated when {@value #ENV_BOOTSTRAP_SERVERS} is set. All other variables are optional.
 *
 * <table>
 *   <tr><th>Variable</th><th>Default</th></tr>
 *   <tr><td>{@value #ENV_BOOTSTRAP_SERVERS}</td><td>—</td></tr>
 *   <tr><td>{@value #ENV_TOPIC}</td><td>mockjdbc.query.events</td></tr>
 *   <tr><td>{@value #ENV_GROUP_ID}</td><td>mockjdbc-wiremock-bridge</td></tr>
 *   <tr><td>{@value #ENV_POLL_TIMEOUT_MS}</td><td>250</td></tr>
 *   <tr><td>{@value #ENV_DATASOURCE_ALLOWLIST}</td><td>(none)</td></tr>
 *   <tr><td>{@value #ENV_SQL_DENY_PREFIXES}</td><td>(none)</td></tr>
 * </table>
 */
public final class WireMockKafkaBridgeExtensionFactory implements ExtensionFactory {

    static final String ENV_BOOTSTRAP_SERVERS = "MOCKJDBC_KAFKA_BOOTSTRAP_SERVERS";
    static final String ENV_TOPIC = "MOCKJDBC_KAFKA_TOPIC";
    static final String ENV_GROUP_ID = "MOCKJDBC_KAFKA_GROUP_ID";
    static final String ENV_POLL_TIMEOUT_MS = "MOCKJDBC_KAFKA_POLL_TIMEOUT_MS";
    static final String ENV_DATASOURCE_ALLOWLIST = "MOCKJDBC_DATASOURCE_ALLOWLIST";
    static final String ENV_SQL_DENY_PREFIXES = "MOCKJDBC_SQL_DENY_PREFIXES";

    @Override
    public List<Extension> create(WireMockServices services) {
        return create(services, System.getenv());
    }

    // Visible for testing
    List<Extension> create(WireMockServices services, Map<String, String> env) {
        String bootstrapServers = env.get(ENV_BOOTSTRAP_SERVERS);
        if (bootstrapServers == null || bootstrapServers.isBlank()) {
            return List.of();
        }

        String topic = env.getOrDefault(ENV_TOPIC, "mockjdbc.query.events");
        String groupId = env.getOrDefault(ENV_GROUP_ID, "mockjdbc-wiremock-bridge");
        long pollTimeoutMs;
        try {
            pollTimeoutMs = Long.parseLong(env.getOrDefault(ENV_POLL_TIMEOUT_MS, "250"));
        } catch (NumberFormatException ignored) {
            pollTimeoutMs = 250L;
        }
        List<String> datasourceAllowlist = csvToList(env.get(ENV_DATASOURCE_ALLOWLIST));
        List<String> sqlDenyPrefixes = csvToList(env.get(ENV_SQL_DENY_PREFIXES));

        var config = new KafkaMappingConsumerConfig(
                bootstrapServers,
                topic,
                groupId,
                services.getAdmin(),
                datasourceAllowlist,
                sqlDenyPrefixes,
                Duration.ofMillis(pollTimeoutMs)
        );

        return List.of(new WireMockKafkaBridgeExtension(config));
    }

    private static List<String> csvToList(String csv) {
        if (csv == null || csv.isBlank()) {
            return List.of();
        }
        return List.of(csv.split(",")).stream()
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }
}

