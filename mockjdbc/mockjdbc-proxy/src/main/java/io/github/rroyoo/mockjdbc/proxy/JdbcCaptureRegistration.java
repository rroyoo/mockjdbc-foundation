package io.github.rroyoo.mockjdbc.proxy;

import io.github.rroyoo.mockjdbc.mock.MockedQuery;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Per-datasource capture binding.
 *
 * <p>Holds the {@link JdbcExecutionCapture} and exposes the public observability API:
 * captured event list, datasource identifier, and lifecycle management.
 *
 * <p>Obtain instances via {@link JdbcAgentRegistry#register}.
 */
public final class JdbcCaptureRegistration implements AutoCloseable {

    private final String datasourceId;
    private final JdbcExecutionCapture capture;

    JdbcCaptureRegistration(String datasourceId, JdbcExecutionCapture capture) {
        this.datasourceId = Objects.requireNonNull(datasourceId, "datasourceId is required");
        this.capture = Objects.requireNonNull(capture, "capture is required");
    }

    /** Returns the logical datasource identifier for this registration. */
    public String datasourceId() {
        return datasourceId;
    }

    /** Returns an immutable snapshot of all intercepted events so far. */
    public List<JdbcQueryInterceptedEvent> events() {
        return capture.events();
    }

    /** Clears the in-memory event buffer. */
    public void clear() {
        capture.clear();
    }

    /**
     * Returns a factory that creates a new registration bound to the provided Kafka-style proto
     * producer and dispatch config — convenience for production wiring without needing a DataSource
     * instance at registration time.
     */
    public static JdbcCaptureRegistration standalone(String datasourceId,
                                                     Consumer<JdbcQueryInterceptedEvent> localConsumer,
                                                     MockedQueryEventProducer producer,
                                                     AsyncDispatchConfig config) {
        var capture = new JdbcExecutionCapture(datasourceId, localConsumer, producer, config);
        return new JdbcCaptureRegistration(datasourceId, capture);
    }

    /** Package-private: used by the agent connection wrappers. */
    JdbcExecutionCapture capture() {
        return capture;
    }

    @Override
    public void close() {
        capture.close();
    }
}
