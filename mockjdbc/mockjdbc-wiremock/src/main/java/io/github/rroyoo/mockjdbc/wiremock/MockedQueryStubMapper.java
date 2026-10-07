package io.github.rroyoo.mockjdbc.wiremock;

import com.github.tomakehurst.wiremock.client.MappingBuilder;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.matching.StringValuePattern;
import com.github.tomakehurst.wiremock.stubbing.StubMapping;
import io.github.rroyoo.mockjdbc.mock.JdbcValue;
import io.github.rroyoo.mockjdbc.mock.MockedQuery;
import io.github.rroyoo.mockjdbc.mock.ParameterMetadata;
import io.github.rroyoo.mockjdbc.mock.QueryExecutionStatus;
import io.github.rroyoo.mockjdbc.mock.SerializedResultSet;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

final class MockedQueryStubMapper {

    /**
     * Full gRPC method path as used by the WireMock gRPC extension for URL matching.
     * Format: /{package}.{ServiceName}/{MethodName}
     */
    static final String GRPC_METHOD_URL =
            "/io.github.rroyoo.mockjdbc.mock.MockQueryService/FindMock";

    private static final Pattern MULTI_SPACE = Pattern.compile("\\s+");

    private final Set<String> datasourceAllowlist;
    private final List<String> sqlDenyPrefixes;

    MockedQueryStubMapper(List<String> datasourceAllowlist, List<String> sqlDenyPrefixes) {
        // HashSet for O(1) allowlist lookup instead of O(n) stream scan.
        this.datasourceAllowlist = datasourceAllowlist == null ? Set.of() : new HashSet<>(datasourceAllowlist);
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

        var params = extractParameters(event);

        // WireMock-gRPC intercepts calls at the gRPC method path and converts the binary
        // protobuf request body to JSON before applying stub matchers.
        // QueryLookupRequest has no datasource_id field — filter on $.sql + each parameter value.
        var mappingBuilder = WireMock.post(WireMock.urlPathEqualTo(GRPC_METHOD_URL))
                .withRequestBody(WireMock.matchingJsonPath("$.sql", exact(normalizedSql)));

        addParameterMatchers(mappingBuilder, params);

        if (event.getStatus() == QueryExecutionStatus.QUERY_EXECUTION_STATUS_ERROR) {
            // Return a MockedQuery JSON with the error field; the mock driver checks hasError().
            mappingBuilder.willReturn(WireMock.aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withHeader("grpc-status-name", "OK")
                    .withBody(buildErrorJsonBody(event)));
        } else {
            // Return a MockedQuery JSON with the resultSet field.
            // WireMock-gRPC converts this JSON back to binary MockedQuery protobuf
            // using the mounted .dsc descriptor file before sending the gRPC response.
            mappingBuilder.willReturn(WireMock.aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withHeader("grpc-status-name", "OK")
                    .withBody(buildSuccessJsonBody(event.getResultSet())));
        }

        // Key includes parameter values so each unique (sql + params) combination gets its own stub.
        var paramKey = paramKeySegment(params);
        var stubKey  = stubKey(datasourceId, normalizedSql, paramKey);
        var mapping  = mappingBuilder.build();
        mapping.setId(UUID.nameUUIDFromBytes(stubKey.getBytes(StandardCharsets.UTF_8)));
        mapping.setName(stubKey);
        return Optional.of(mapping);
    }

    // ── Parameter matching ────────────────────────────────────────────────────

    /**
     * Adds one {@code matchesJsonPath} body pattern per captured parameter.
     * WireMock-gRPC converts the binary {@code QueryLookupRequest} to JSON before
     * matching, using standard protobuf JSON encoding (int64 → quoted string).
     */
    private static void addParameterMatchers(MappingBuilder builder,
                                             List<ParameterMetadata> params) {
        for (int i = 0; i < params.size(); i++) {
            var value = params.get(i).getValue();
            var path  = paramJsonPath(i, value);
            var expected = paramExpectedValue(value);
            if (path != null && expected != null) {
                builder.withRequestBody(WireMock.matchingJsonPath(path, exact(expected)));
            }
        }
    }

    /**
     * Returns the JSONPath expression for the given parameter's value field,
     * or {@code null} for kinds that are not suitable for exact matching (e.g. NULL, bytes).
     */
    static String paramJsonPath(int zeroBasedIndex, JdbcValue value) {
        var field = switch (value.getKindCase()) {
            case LONG_VAL    -> "longVal";
            case STRING_VAL  -> "stringVal";
            case BOOL_VAL    -> "boolVal";
            case DOUBLE_VAL  -> "doubleVal";
            case DECIMAL_VAL -> "decimalVal";
            default -> null;  // IS_NULL, BYTES_VAL, TIMESTAMP_VAL — skip exact match
        };
        if (field == null) return null;
        return "$.parameters[" + zeroBasedIndex + "].value." + field;
    }

    /**
     * Returns the expected string value for the given JdbcValue, or {@code null} if not matchable.
     * int64 is returned as a plain decimal string because protobuf JSON encodes it that way.
     */
    static String paramExpectedValue(JdbcValue value) {
        return switch (value.getKindCase()) {
            case LONG_VAL    -> String.valueOf(value.getLongVal());
            case STRING_VAL  -> value.getStringVal();
            case BOOL_VAL    -> String.valueOf(value.getBoolVal());
            case DOUBLE_VAL  -> String.valueOf(value.getDoubleVal());
            case DECIMAL_VAL -> value.getDecimalVal();
            default -> null;
        };
    }

    /** Stable string segment encoding all captured parameter values for use in the stub key. */
    private static String paramKeySegment(List<ParameterMetadata> params) {
        if (params.isEmpty()) return "";
        var sb = new StringBuilder();
        for (var p : params) {
            var val = paramExpectedValue(p.getValue());
            sb.append("::p").append(p.getIndex()).append('=').append(val != null ? val : "null");
        }
        return sb.toString();
    }

    private static List<ParameterMetadata> extractParameters(MockedQuery event) {
        return switch (event.getStatementDefinitionCase()) {
            case PREPARED_STATEMENT -> event.getPreparedStatement().getParametersList();
            case CALLABLE_STATEMENT -> event.getCallableStatement().getParametersList();
            default -> List.of();
        };
    }

    // ── JSON body builders ────────────────────────────────────────────────────

    /**
     * Builds a MockedQuery JSON body with the resultSet field populated.
     * Follows the protobuf JSON encoding conventions (int64 as string, camelCase field names).
     */
    static String buildSuccessJsonBody(SerializedResultSet rs) {
        var sb = new StringBuilder("{\"resultSet\":{\"metadata\":[");
        for (int i = 0; i < rs.getMetadataCount(); i++) {
            if (i > 0) sb.append(',');
            var m = rs.getMetadata(i);
            sb.append("{\"name\":").append(jsonString(m.getName()))
              .append(",\"label\":").append(jsonString(m.getLabel()))
              .append(",\"sqlType\":").append(m.getSqlType())
              .append(",\"typeName\":").append(jsonString(m.getTypeName()))
              .append('}');
        }
        sb.append("],\"rows\":[");
        for (int i = 0; i < rs.getRowsCount(); i++) {
            if (i > 0) sb.append(',');
            var row = rs.getRows(i);
            sb.append("{\"values\":[");
            for (int j = 0; j < row.getValuesCount(); j++) {
                if (j > 0) sb.append(',');
                sb.append(jdbcValueJson(row.getValues(j)));
            }
            sb.append("]}");
        }
        sb.append("]}}");
        return sb.toString();
    }

    /**
     * Builds a MockedQuery JSON body with the error field populated.
     * The mock driver checks {@code response.hasError()} and throws the right SQLException.
     */
    static String buildErrorJsonBody(MockedQuery event) {
        var type = event.hasError() ? event.getError().getType() : "";
        var message = event.hasError() ? event.getError().getMessage() : "";
        return "{\"error\":{\"type\":" + jsonString(type) + ",\"message\":" + jsonString(message) + "}}";
    }

    /**
     * Converts a {@link JdbcValue} to its protobuf JSON representation.
     * int64 values are encoded as strings per the protobuf JSON spec.
     */
    static String jdbcValueJson(JdbcValue v) {
        return switch (v.getKindCase()) {
            case STRING_VAL  -> "{\"stringVal\":"  + jsonString(v.getStringVal()) + "}";
            case LONG_VAL    -> "{\"longVal\":\""  + v.getLongVal()  + "\"}";  // int64 → quoted string
            case DOUBLE_VAL  -> "{\"doubleVal\":"  + v.getDoubleVal() + "}";
            case BOOL_VAL    -> "{\"boolVal\":"    + v.getBoolVal()  + "}";
            case BYTES_VAL   -> "{\"bytesVal\":"   + jsonString(Base64.getEncoder()
                                    .encodeToString(v.getBytesVal().toByteArray())) + "}";
            case DECIMAL_VAL -> "{\"decimalVal\":" + jsonString(v.getDecimalVal()) + "}";
            case TIMESTAMP_VAL -> "{\"timestampVal\":" + jsonString(
                                    Instant.ofEpochSecond(
                                        v.getTimestampVal().getSeconds(),
                                        v.getTimestampVal().getNanos()).toString()) + "}";
            case IS_NULL     -> "{\"isNull\":true}";
            default          -> "{}";
        };
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

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

    /** Stable unique key incorporating datasource, SQL, and all parameter values. */
    static String stubKey(String datasourceId, String normalizedSql, String paramKey) {
        return datasourceId + "::" + normalizedSql + paramKey;
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
        return datasourceAllowlist.contains(datasourceId);
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

    /** Escapes a string for embedding inside a JSON string value. */
    private static String jsonString(String value) {
        if (value == null) return "\"\"";
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
