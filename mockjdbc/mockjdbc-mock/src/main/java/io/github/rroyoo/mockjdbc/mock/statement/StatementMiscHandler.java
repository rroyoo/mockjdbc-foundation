package io.github.rroyoo.mockjdbc.mock.statement;

import io.github.rroyoo.mockjdbc.mock.connection.ConnectionFactory;
import io.github.rroyoo.mockjdbc.mock.driver.MockConfig;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicBoolean;

final class StatementMiscHandler {

    private final MockConfig mockConfig;
    private final StatementLifecycleHandler lifecycle;
    private final AtomicBoolean poolable = new AtomicBoolean(true);
    private final AtomicBoolean escapeProcessing = new AtomicBoolean(true);

    StatementMiscHandler(MockConfig mockConfig, StatementLifecycleHandler lifecycle) {
        this.mockConfig = mockConfig;
        this.lifecycle = lifecycle;
    }

    public void setPoolable(boolean poolable) throws SQLException {
        lifecycle.assertOpen();
        this.poolable.set(poolable);
    }

    public boolean isPoolable() throws SQLException {
        lifecycle.assertOpen();
        return poolable.get();
    }

    public void setEscapeProcessing(boolean enable) throws SQLException {
        lifecycle.assertOpen();
        escapeProcessing.set(enable);
    }

    public void setCursorName(String name) throws SQLException {
        lifecycle.assertOpen();
        // No-op: cursor naming is not applicable to mock statements.
    }

    public void cancel() throws SQLException {
        // No-op: statement cancellation is not applicable to mock statements.
    }

    public Connection getConnection() throws SQLException {
        lifecycle.assertOpen();
        return ConnectionFactory.create(mockConfig);
    }
}

