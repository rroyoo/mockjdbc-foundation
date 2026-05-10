package io.github.rroyoo.mockjdbc.wiremock;

import com.github.tomakehurst.wiremock.core.Admin;

import java.time.Duration;
import java.util.List;

public record KafkaMappingConsumerConfig(
        String bootstrapServers,
        String topic,
        String groupId,
        Admin admin,
        List<String> datasourceAllowlist,
        List<String> sqlDenyPrefixes,
        Duration pollTimeout
) {

    public KafkaMappingConsumerConfig {
        if (isBlank(bootstrapServers)) {
            throw new IllegalArgumentException("bootstrapServers is required");
        }
        if (isBlank(topic)) {
            throw new IllegalArgumentException("topic is required");
        }
        if (isBlank(groupId)) {
            throw new IllegalArgumentException("groupId is required");
        }
        if (admin == null) {
            throw new IllegalArgumentException("admin is required");
        }

        datasourceAllowlist = datasourceAllowlist == null ? List.of() : List.copyOf(datasourceAllowlist);
        sqlDenyPrefixes = sqlDenyPrefixes == null ? List.of() : List.copyOf(sqlDenyPrefixes);
        pollTimeout = pollTimeout == null ? Duration.ofMillis(250) : pollTimeout;

        if (pollTimeout.isZero() || pollTimeout.isNegative()) {
            throw new IllegalArgumentException("pollTimeout must be > 0");
        }
    }

    public static KafkaMappingConsumerConfig defaults(String bootstrapServers,
                                                      String topic,
                                                      String groupId,
                                                      Admin admin) {
        return new KafkaMappingConsumerConfig(
                bootstrapServers,
                topic,
                groupId,
                admin,
                List.of(),
                List.of(),
                Duration.ofMillis(250)
        );
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}

