package io.github.rroyoo.mockjdbc.proxy;

import net.bytebuddy.asm.Advice;

import javax.sql.DataSource;
import java.sql.Connection;

/**
 * ByteBuddy {@link Advice} class that intercepts {@link DataSource#getConnection()} at the exit
 * point and replaces the returned {@link Connection} with a capturing proxy when the DataSource
 * is registered in {@link JdbcAgentRegistry}.
 *
 * <p>This class is inlined by ByteBuddy into the instrumented DataSource class. All referenced
 * types ({@link JdbcAgentRegistry}) must therefore be resolvable by the target class's
 * classloader — which is guaranteed because the {@code mockjdbc-proxy} jar is on the application
 * classpath.
 *
 * <p><strong>Guardrails:</strong>
 * <ul>
 *   <li>The advice is a pure exit-point hook; it never modifies arguments or suppresses exceptions.</li>
 *   <li>If wrapping fails for any reason, {@link JdbcAgentRegistry#wrapIfRegistered} returns the
 *       original connection unchanged — the application never breaks.</li>
 * </ul>
 */
final class DataSourceGetConnectionAdvice {

    private DataSourceGetConnectionAdvice() {}

    @Advice.OnMethodExit(onThrowable = Throwable.class)
    public static void onGetConnectionExit(
            @Advice.This Object self,
            @Advice.Return(readOnly = false) Connection returned,
            @Advice.Thrown Throwable thrown) {
        if (thrown != null || returned == null) {
            return;
        }
        if (!(self instanceof DataSource)) {
            return;
        }
        returned = JdbcAgentRegistry.wrapIfRegistered((DataSource) self, returned);
    }
}
