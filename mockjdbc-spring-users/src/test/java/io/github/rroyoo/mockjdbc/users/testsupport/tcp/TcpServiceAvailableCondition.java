package io.github.rroyoo.mockjdbc.users.testsupport.tcp;

import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtensionContext;

import java.util.Optional;

public final class TcpServiceAvailableCondition implements ExecutionCondition {

    @Override
    public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext context) {
        var annotation = context.getElement()
                .flatMap(element -> Optional.ofNullable(element.getAnnotation(RequiresTcpService.class)))
                .or(() -> context.getTestClass().map(clazz -> clazz.getAnnotation(RequiresTcpService.class)));

        if (annotation.isEmpty()) {
            return ConditionEvaluationResult.enabled("No TCP service requirement configured");
        }

        var requiresTcpService = annotation.get();
        var host = resolveHost(requiresTcpService);
        var port = resolvePort(requiresTcpService);
        var timeoutMs = Math.max(1, requiresTcpService.timeoutMs());

        if (port <= 0 || port > 65535) {
            return ConditionEvaluationResult.disabled(
                    "Skipping test: invalid port " + port + " for " + requiresTcpService.name());
        }

        if (TcpServiceProbe.isReachable(host, port, timeoutMs)) {
            return ConditionEvaluationResult.enabled(
                    "TCP service reachable for " + requiresTcpService.name() + " at " + host + ":" + port);
        }

        return ConditionEvaluationResult.disabled(
                "Skipping test: " + requiresTcpService.name() + " is not reachable at " + host + ":" + port);
    }

    private static String resolveHost(RequiresTcpService annotation) {
        if (!annotation.hostEnv().isBlank()) {
            var hostFromEnv = System.getenv(annotation.hostEnv());
            if (hostFromEnv != null && !hostFromEnv.isBlank()) {
                return hostFromEnv;
            }
        }
        return annotation.host();
    }

    private static int resolvePort(RequiresTcpService annotation) {
        if (!annotation.portEnv().isBlank()) {
            var portFromEnv = System.getenv(annotation.portEnv());
            if (portFromEnv != null && !portFromEnv.isBlank()) {
                try {
                    return Integer.parseInt(portFromEnv);
                } catch (NumberFormatException ignored) {
                    return -1;
                }
            }
        }
        return annotation.port();
    }
}
