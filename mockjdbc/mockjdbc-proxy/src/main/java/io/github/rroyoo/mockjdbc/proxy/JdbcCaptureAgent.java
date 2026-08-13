package io.github.rroyoo.mockjdbc.proxy;

import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.asm.Advice;

import javax.sql.DataSource;
import java.lang.instrument.Instrumentation;

import static net.bytebuddy.matcher.ElementMatchers.isSubTypeOf;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.nameStartsWith;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;
import static net.bytebuddy.matcher.ElementMatchers.takesNoArguments;

/**
 * Java agent entry point that installs ByteBuddy instrumentation on all {@link DataSource}
 * subtypes to capture JDBC query executions and ResultSet data.
 *
 * <p><strong>How it works:</strong>
 * <ol>
 *   <li>On JVM startup ({@code premain}) or dynamic attachment ({@code agentmain}), an
 *       {@link AgentBuilder} intercepts {@code DataSource.getConnection()} on every concrete
 *       DataSource subtype.</li>
 *   <li>The {@link DataSourceGetConnectionAdvice} modifies the returned {@link java.sql.Connection}
 *       to a capturing proxy if the DataSource has been registered via
 *       {@link JdbcAgentRegistry#register}.</li>
 *   <li>The capturing Connection proxy wraps each Statement, PreparedStatement, and
 *       CallableStatement it creates, collecting SQL, parameters, update counts, and row data.</li>
 *   <li>Captured data is published asynchronously as {@link io.github.rroyoo.mockjdbc.mock.MockedQuery}
 *       protobuf events via the configured {@link MockedQueryEventProducer} (e.g., Kafka).</li>
 * </ol>
 *
 * <p><strong>Usage as JVM agent:</strong>
 * <pre>{@code
 * java -javaagent:/path/to/mockjdbc-proxy.jar -jar my-app.jar
 * }</pre>
 *
 * <p><strong>Programmatic installation (tests or dynamic attachment):</strong>
 * <pre>{@code
 * Instrumentation inst = ByteBuddyAgent.install();
 * JdbcCaptureAgent.install(inst);
 * JdbcAgentRegistry.register(dataSource, "users-primary", localConsumer, kafkaProducer, config);
 * }</pre>
 */
public final class JdbcCaptureAgent {

    private JdbcCaptureAgent() {}

    /** JVM entry point for static agent loading ({@code -javaagent}). */
    public static void premain(String agentArgs, Instrumentation instrumentation) {
        install(instrumentation);
    }

    /** JVM entry point for dynamic agent attachment. */
    public static void agentmain(String agentArgs, Instrumentation instrumentation) {
        install(instrumentation);
    }

    /**
     * Programmatic installation — use when embedding in tests or framework integrations
     * without requiring a JVM agent flag.
     *
     * @param instrumentation the JVM {@link Instrumentation} instance
     */
    public static void install(Instrumentation instrumentation) {
        new AgentBuilder.Default()
                // Avoid retransforming our own proxy/wrapper classes to prevent infinite recursion
                .ignore(nameStartsWith("io.github.rroyoo.mockjdbc.proxy.Capturing")
                        .or(nameStartsWith("io.github.rroyoo.mockjdbc.proxy.Jdbc")))
                .with(AgentBuilder.RedefinitionStrategy.RETRANSFORMATION)
                .with(AgentBuilder.InitializationStrategy.NoOp.INSTANCE)
                .with(AgentBuilder.TypeStrategy.Default.REDEFINE)
                .type(isSubTypeOf(DataSource.class)
                        .and(not(nameStartsWith("io.github.rroyoo.mockjdbc"))))
                .transform((builder, typeDescription, classLoader, module, protectionDomain) ->
                        builder
                                // DataSource.getConnection() — no credentials
                                .method(named("getConnection").and(takesNoArguments()))
                                .intercept(Advice.to(DataSourceGetConnectionAdvice.class))
                                // DataSource.getConnection(String user, String password)
                                .method(named("getConnection")
                                        .and(takesArguments(String.class, String.class)))
                                .intercept(Advice.to(DataSourceGetConnectionAdvice.class))
                )
                .installOn(instrumentation);
    }
}
