package io.github.rroyoo.mockjdbc.mock.connection;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLWarning;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public final class ConnectionStateHandler {

    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final AtomicBoolean autoCommit = new AtomicBoolean(true);  // JDBC default
    private final AtomicBoolean readOnly = new AtomicBoolean(false);   // JDBC default
    private final AtomicInteger transactionIsolation = new AtomicInteger(Connection.TRANSACTION_READ_COMMITTED); // JDBC default
    private final AtomicInteger holdability = new AtomicInteger(java.sql.ResultSet.CLOSE_CURSORS_AT_COMMIT);    // JDBC default
    private final AtomicReference<String> catalog = new AtomicReference<>(null);
    private final AtomicReference<String> schema = new AtomicReference<>(null);
    private final AtomicInteger networkTimeout = new AtomicInteger(0); // JDBC default: no timeout
    private final AtomicReference<SQLWarning> warnings = new AtomicReference<>(null);

    public void close() throws SQLException { closed.set(true); }
    public boolean isClosed() throws SQLException { return closed.get(); }

    public void setAutoCommit(boolean autoCommit) throws SQLException { this.autoCommit.set(autoCommit); }
    public boolean getAutoCommit() throws SQLException { return autoCommit.get(); }

    public void setReadOnly(boolean readOnly) throws SQLException { this.readOnly.set(readOnly); }
    public boolean isReadOnly() throws SQLException { return readOnly.get(); }

    public void setTransactionIsolation(int level) throws SQLException { transactionIsolation.set(level); }
    public int getTransactionIsolation() throws SQLException { return transactionIsolation.get(); }

    public void setHoldability(int holdability) throws SQLException { this.holdability.set(holdability); }
    public int getHoldability() throws SQLException { return holdability.get(); }

    public void setCatalog(String catalog) throws SQLException { this.catalog.set(catalog); }
    public String getCatalog() throws SQLException { return catalog.get(); }

    public void setSchema(String schema) throws SQLException { this.schema.set(schema); }
    public String getSchema() throws SQLException { return schema.get(); }

    public void setNetworkTimeout(java.util.concurrent.Executor executor, int milliseconds) throws SQLException { networkTimeout.set(milliseconds); }
    public int getNetworkTimeout() throws SQLException { return networkTimeout.get(); }

    public SQLWarning getWarnings() throws SQLException { return warnings.get(); }

    public void clearWarnings() throws SQLException { warnings.set(null); }

    public void addWarning(SQLWarning warning) {
        warnings.accumulateAndGet(warning, (existing, next) -> {
            if (existing == null) return next;
            existing.setNextWarning(next);
            return existing;
        });
    }
}
