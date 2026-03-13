package io.github.rroyoo.mockjdbc.mock.statement;

import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicBoolean;

final class StatementLifecycleHandler {

    private final AtomicBoolean closed = new AtomicBoolean(false);

    public void close() throws SQLException {
        closed.set(true);
    }

    public boolean isClosed() throws SQLException {
        return closed.get();
    }

    public void assertOpen() throws SQLException {
        if (closed.get()) {
            throw new SQLException("Statement is closed");
        }
    }
}

