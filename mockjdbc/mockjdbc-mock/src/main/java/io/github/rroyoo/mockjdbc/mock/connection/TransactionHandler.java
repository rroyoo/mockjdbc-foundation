package io.github.rroyoo.mockjdbc.mock.connection;

import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicBoolean;

public final class TransactionHandler {

    private final AtomicBoolean autoCommit = new AtomicBoolean(true); // JDBC default

    public void setAutoCommit(boolean autoCommit) throws SQLException { this.autoCommit.set(autoCommit); }

    public boolean getAutoCommit() throws SQLException { return autoCommit.get(); }
}

