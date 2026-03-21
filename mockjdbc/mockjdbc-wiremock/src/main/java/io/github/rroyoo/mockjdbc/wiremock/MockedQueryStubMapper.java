package io.github.rroyoo.mockjdbc.wiremock;

import com.github.tomakehurst.wiremock.client.MappingBuilder;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.matching.StringValuePattern;
import com.github.tomakehurst.wiremock.stubbing.StubMapping;
import io.github.rroyoo.mockjdbc.mock.MockedQuery;
import io.github.rroyoo.mockjdbc.mock.QueryExecutionStatus;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

final class MockedQueryStubMapper {

    private static final Pattern MULTI_SPACE = Pattern.compile("\\s+");

    private final List<String> datasourceAllowlist;
    private final List<String> sqlDenyPrefixes;

    MockedQueryStubMapper(List<String> datasourceAllowlist, List<String> sqlDenyPrefixes) {
        this.datasourceAllowlist = datasourceAllowlist == null ? List.of() : List.copyOf(datasourceAllowlist);
        this.sqlDenyPrefixes = sqlDenyPrefixes == null ? List.of() : List.copyOf(sqlDenyPrefixes);
    }

    Optional<StubMapping> map(MockedQuery event) {
        if (event == null) {
            return Optional.empty();
        }

        var datasourceId = normalizedDatasource(event.getDatasourceId());
        if (!datasourceAllowed(datasourceId)) {
            return Optional.empty();
        }

        var sql = extractSql(event);
        var normalizedSql = normalizeSql(sql);
        if (normalizedSql.isEmpty() || denied(normalizedSql)) {
            return Optional.empty();
        }

        var mappingBuilder = WireMock.post(WireMock.urlEqualTo("/mockjdbc/query"))
                .withRequestBody(WireMock.matchingJsonPath("$.sql", exact(normalizedSql)))
                .withRequestBody(WireMock.matchingJsonPath("$.datasourceId", exact(datasourceId)));

        if (event.getStatus() == QueryExecutionStatus.QUERY_EXECUTION_STATUS_ERROR) {
            var errorType = event.hasError() ? event.getError().getType() : "";
            var errorMessage = event.hasError() ? event.getError().getMessage() : "";
            var errorJson = "{\"type\":\"" + escapeJson(errorType) + "\",\"message\":\"" + escapeJson(errorMessage) + "\"}";
            mappingBuilder.willReturn(WireMock.aResponse()
                    .withStatus(500)
                    .withHeader("Content-Type", "application/json")
                    .withBody(errorJson));
        } else {
            mappingBuilder.willReturn(WireMock.aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/x-protobuf")
                    .withBody(event.getResultSet().toByteArray()));
        }

        var mapping = mappingBuilder.build();
        mapping.setId(stubId(datasourceId, normalizedSql));
        mapping.setName(stubKey(datasourceId, normalizedSql));
        return Optional.of(mapping);
    }

    static String normalizeSql(String sql) {
        if (sql == null) {
            return "";
        }
        var normalized = MULTI_SPACE.matcher(sql.strip().toLowerCase(Locale.ROOT)).replaceAll(" ");
        while (normalized.endsWith(";")) {
            normalized = normalized.substring(0, normalized.length() - 1).strip();
        }
        return normalized;
    }

    static UUID stubId(String datasourceId, String normalizedSql) {
        return UUID.nameUUIDFromBytes(stubKey(datasourceId, normalizedSql).getBytes(StandardCharsets.UTF_8));
    }

    private static String stubKey(String datasourceId, String normalizedSql) {
        return datasourceId + "::" + normalizedSql;
    }

    private static String extractSql(MockedQuery event) {
        return switch (event.getStatementDefinitionCase()) {
            case SIMPLE_STATEMENT -> event.getSimpleStatement().getSql();
            case PREPARED_STATEMENT -> event.getPreparedStatement().getSql();
            case CALLABLE_STATEMENT -> event.getCallableStatement().getCallSql();
            case STATEMENTDEFINITION_NOT_SET -> "";
        };
    }

    private boolean datasourceAllowed(String datasourceId) {
        if (datasourceAllowlist.isEmpty()) {
            return true;
        }
        return datasourceAllowlist.stream().anyMatch(id -> id.equals(datasourceId));
    }

    private boolean denied(String normalizedSql) {
        for (var prefix : sqlDenyPrefixes) {
            if (prefix != null && !prefix.isBlank() && normalizedSql.startsWith(prefix.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private static String normalizedDatasource(String datasourceId) {
        if (datasourceId == null || datasourceId.isBlank()) {
            return "default-datasource";
        }
        return datasourceId.trim();
    }

    private static StringValuePattern exact(String value) {
        return WireMock.equalTo(value);
    }

    private static String escapeJson(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}

