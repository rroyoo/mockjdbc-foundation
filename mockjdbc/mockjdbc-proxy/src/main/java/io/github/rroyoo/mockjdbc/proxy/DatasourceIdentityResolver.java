package io.github.rroyoo.mockjdbc.proxy;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Connection;
import java.util.HexFormat;

final class DatasourceIdentityResolver {

    private static final String UNKNOWN = "datasource-unknown";

    private DatasourceIdentityResolver() {}

    static String resolve(String explicitAlias, String beanName, DataSource dataSource) {
        if (hasText(explicitAlias)) {
            return explicitAlias.trim();
        }
        if (hasText(beanName)) {
            return beanName.trim();
        }
        return fingerprint(dataSource);
    }

    static String fingerprint(DataSource dataSource) {
        if (dataSource == null) {
            return UNKNOWN;
        }

        try (Connection connection = dataSource.getConnection()) {
            var metaData = connection.getMetaData();
            if (metaData == null) {
                return UNKNOWN;
            }

            var basis = safe(metaData.getURL())
                    + "|"
                    + safe(metaData.getUserName())
                    + "|"
                    + safe(metaData.getDriverName());
            var digest = MessageDigest.getInstance("SHA-256").digest(basis.getBytes(StandardCharsets.UTF_8));
            return "ds-" + HexFormat.of().formatHex(digest, 0, 8);
        } catch (Exception ignored) {
            return UNKNOWN;
        }
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}

