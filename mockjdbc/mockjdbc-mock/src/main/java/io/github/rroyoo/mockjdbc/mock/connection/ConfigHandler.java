package io.github.rroyoo.mockjdbc.mock.connection;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

final class ConfigHandler {

    private final AtomicBoolean readOnly = new AtomicBoolean(false);                                            // JDBC default
    private final AtomicInteger transactionIsolation = new AtomicInteger(Connection.TRANSACTION_READ_COMMITTED); // JDBC default
    private final AtomicInteger holdability = new AtomicInteger(ResultSet.CLOSE_CURSORS_AT_COMMIT);             // JDBC default
    private final AtomicReference<String> catalog = new AtomicReference<>(null);
    private final AtomicReference<String> schema = new AtomicReference<>(null);
    private final AtomicInteger networkTimeout = new AtomicInteger(0);                                          // JDBC default: no timeout

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

    public void setNetworkTimeout(Executor executor, int milliseconds) throws SQLException { networkTimeout.set(milliseconds); }
    public int getNetworkTimeout() throws SQLException { return networkTimeout.get(); }
}
