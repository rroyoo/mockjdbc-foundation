package io.github.rroyoo.mockjdbc.mock.connection;

import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicBoolean;

public final class ConnectionStateHandler {

    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final AtomicBoolean autoCommit = new AtomicBoolean(true); // JDBC default

    public void close() throws SQLException {
        closed.set(true);
    }

    public boolean isClosed() throws SQLException {
        return closed.get();
    }

    public void setAutoCommit(boolean autoCommit) throws SQLException {
        this.autoCommit.set(autoCommit);
    }

    public boolean getAutoCommit() throws SQLException {
        return autoCommit.get();
    }
}
