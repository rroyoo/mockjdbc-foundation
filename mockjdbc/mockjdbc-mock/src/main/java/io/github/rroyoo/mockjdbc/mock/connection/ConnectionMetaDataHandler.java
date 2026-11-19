package io.github.rroyoo.mockjdbc.mock.connection;

import io.github.rroyoo.mockjdbc.mock.driver.MockConfig;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Builds a {@link DatabaseMetaData} that answers product, driver and URL questions and rejects
 * everything else with {@link SQLFeatureNotSupportedException}; the mock backend has no catalog to describe.
 */
final class ConnectionMetaDataHandler {

    static final String PRODUCT_NAME = "MockJDBC";
    static final String DRIVER_NAME = "MockJDBC Driver";
    static final String VERSION = "1.0";

    private final MockConfig mockConfig;

    ConnectionMetaDataHandler(MockConfig mockConfig) {
        this.mockConfig = mockConfig;
    }

    public DatabaseMetaData create(Connection connection) {
        var url = "jdbc:mock://" + mockConfig.mockServer().host() + ":" + mockConfig.mockServer().port();
        var supported = supportedAnswers(connection, url);

        InvocationHandler handler = (proxy, method, args) -> {
            if (method.getDeclaringClass() == Object.class) {
                return switch (method.getName()) {
                    case "equals" -> proxy == args[0];
                    case "hashCode" -> System.identityHashCode(proxy);
                    default -> "MockDatabaseMetaData";
                };
            }
            if ("unwrap".equals(method.getName())) {
                return unwrapMetaData((Class<?>) args[0], proxy);
            }
            if ("isWrapperFor".equals(method.getName())) {
                return ((Class<?>) args[0]).isInstance(proxy);
            }
            var answer = supported.get(method.getName());
            if (answer != null && method.getParameterCount() == 0) {
                return answer.get();
            }
            throw new SQLFeatureNotSupportedException(
                    "DatabaseMetaData." + method.getName() + " is not supported by MockJDBC");
        };

        return (DatabaseMetaData) Proxy.newProxyInstance(
                ConnectionMetaDataHandler.class.getClassLoader(),
                new Class<?>[]{ DatabaseMetaData.class },
                handler);
    }

    private static Object unwrapMetaData(Class<?> iface, Object proxy) throws SQLException {
        if (iface != null && iface.isInstance(proxy)) {
            return proxy;
        }
        throw new SQLException("Not a wrapper for " + iface);
    }

    private static Map<String, Supplier<Object>> supportedAnswers(Connection connection, String url) {
        return Map.ofEntries(
                Map.entry("getURL", () -> url),
                Map.entry("getUserName", () -> ""),
                Map.entry("getDatabaseProductName", () -> PRODUCT_NAME),
                Map.entry("getDatabaseProductVersion", () -> VERSION),
                Map.entry("getDatabaseMajorVersion", () -> 1),
                Map.entry("getDatabaseMinorVersion", () -> 0),
                Map.entry("getDriverName", () -> DRIVER_NAME),
                Map.entry("getDriverVersion", () -> VERSION),
                Map.entry("getDriverMajorVersion", () -> 1),
                Map.entry("getDriverMinorVersion", () -> 0),
                Map.entry("getJDBCMajorVersion", () -> 4),
                Map.entry("getJDBCMinorVersion", () -> 3),
                Map.entry("getConnection", () -> connection),
                Map.entry("supportsBatchUpdates", () -> true),
                Map.entry("supportsGetGeneratedKeys", () -> true)
        );
    }
}
