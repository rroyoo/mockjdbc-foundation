package io.github.rroyoo.mockjdbc.proxy;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;
import java.util.function.Consumer;

/**
 * Global registry that maps {@link DataSource} instances to their {@link JdbcCaptureRegistration}.
 *
 * <p>The Java agent's {@link DataSourceGetConnectionAdvice} looks up the DataSource from this
 * registry on every {@code getConnection()} call to decide whether to wrap the returned
 * {@link Connection}.
 *
 * <p>Uses a {@link WeakHashMap} so that DataSource GC is not prevented by the registry.
 */
public final class JdbcAgentRegistry {

    private static final Map<DataSource, JdbcCaptureRegistration> registry =
            Collections.synchronizedMap(new WeakHashMap<>());

    private JdbcAgentRegistry() {}

    /**
     * Registers a {@link DataSource} for SQL capture.
     *
     * <p>After registration every {@link Connection} obtained via
     * {@code dataSource.getConnection()} will be transparently wrapped so that all statement
     * executions emit {@link JdbcQueryInterceptedEvent} and {@link io.github.rroyoo.mockjdbc.mock.MockedQuery}
     * proto events.
     *
     * @param dataSource     the DataSource to monitor (weak reference; does not prevent GC)
     * @param datasourceId   logical identifier used in all emitted events
     * @param localConsumer  receives the raw {@link JdbcQueryInterceptedEvent} on the calling thread
     * @param producer       async proto event producer (Kafka or consumer-backed)
     * @param config         async dispatch configuration
     * @return a {@link JdbcCaptureRegistration} that exposes the event buffer and lifecycle
     */
    public static JdbcCaptureRegistration register(DataSource dataSource,
                                                   String datasourceId,
                                                   Consumer<JdbcQueryInterceptedEvent> localConsumer,
                                                   MockedQueryEventProducer producer,
                                                   AsyncDispatchConfig config) {
        Objects.requireNonNull(dataSource, "dataSource is required");
        var capture = new JdbcExecutionCapture(datasourceId, localConsumer, producer, config);
        var registration = new JdbcCaptureRegistration(datasourceId, capture);
        registry.put(dataSource, registration);
        return registration;
    }

    /**
     * Looks up a registration for the given DataSource instance.
     *
     * @param dataSource the DataSource returned by the intercepted {@code getConnection()}
     * @return registration or {@code null} if not registered
     */
    public static JdbcCaptureRegistration lookup(DataSource dataSource) {
        if (dataSource == null) {
            return null;
        }
        return registry.get(dataSource);
    }

    /**
     * Removes a previously registered DataSource from the registry.
     * Existing connections already obtained from the DataSource continue to capture until closed.
     */
    public static void unregister(DataSource dataSource) {
        registry.remove(dataSource);
    }

    /**
     * Wraps the given {@link Connection} using the registration associated with {@code dataSource},
     * if one exists. Returns the original connection unchanged if not registered.
     *
     * <p>This method is called by {@link DataSourceGetConnectionAdvice} after {@code getConnection()}
     * returns. It is {@code public} so that ByteBuddy's inlined advice bytecode — which may execute
     * under an unrelated class's context — can resolve the reference.
     */
    public static Connection wrapIfRegistered(DataSource dataSource, Connection connection) {
        if (dataSource == null || connection == null) {
            return connection;
        }
        var registration = registry.get(dataSource);
        if (registration == null) {
            return connection;
        }
        try {
            return CapturingConnectionInvocationHandler.wrap(connection, registration);
        } catch (Exception ignored) {
            // Fail-safe: never break the application due to capture wiring failure
            return connection;
        }
    }
}
