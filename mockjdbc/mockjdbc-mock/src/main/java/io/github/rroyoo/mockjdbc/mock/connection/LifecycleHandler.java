package io.github.rroyoo.mockjdbc.mock.connection;

import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicBoolean;

final class LifecycleHandler {

    private final AtomicBoolean closed = new AtomicBoolean(false);

    public void close() throws SQLException { closed.set(true); }

    public boolean isClosed() throws SQLException { return closed.get(); }
}
