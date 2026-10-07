package io.github.rroyoo.mockjdbc.mock.connection;

import java.sql.SQLException;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

final class LifecycleHandler {

    private final AtomicBoolean closed = new AtomicBoolean(false);

    public void close() throws SQLException { closed.set(true); }

    public boolean isClosed() throws SQLException { return closed.get(); }

    public boolean isValid(int timeoutSeconds) throws SQLException {
        if (timeoutSeconds < 0) {
            throw new SQLException("Timeout must not be negative: " + timeoutSeconds);
        }
        return !closed.get();
    }

    /**
     * Marks the connection as closed and runs the release action on the supplied executor, as
     * required by {@link java.sql.Connection#abort(Executor)}. Aborting a closed connection is a no-op.
     */
    public void abort(Executor executor, Runnable release) throws SQLException {
        if (executor == null) {
            throw new SQLException("Executor must not be null");
        }
        if (closed.compareAndSet(false, true)) {
            executor.execute(release);
        }
    }

    public void ensureOpen() throws SQLException {
        if (closed.get()) {
            throw new SQLException("Connection is closed");
        }
    }
}
