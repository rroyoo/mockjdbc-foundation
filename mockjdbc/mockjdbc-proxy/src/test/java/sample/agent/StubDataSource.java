package sample.agent;

import javax.sql.DataSource;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.SQLFeatureNotSupportedException;
import java.util.logging.Logger;

/** Minimal DataSource living outside the io.github.rroyoo.mockjdbc package so the agent instruments it. */
public final class StubDataSource implements DataSource {

    private final Connection connection;

    public StubDataSource(Connection connection) {
        this.connection = connection;
    }

    @Override public Connection getConnection() { return connection; }
    @Override public Connection getConnection(String username, String password) { return connection; }
    @Override public PrintWriter getLogWriter() { return null; }
    @Override public void setLogWriter(PrintWriter out) { }
    @Override public void setLoginTimeout(int seconds) { }
    @Override public int getLoginTimeout() { return 0; }
    @Override public Logger getParentLogger() throws SQLFeatureNotSupportedException { throw new SQLFeatureNotSupportedException(); }
    @Override public <T> T unwrap(Class<T> iface) { throw new UnsupportedOperationException(); }
    @Override public boolean isWrapperFor(Class<?> iface) { return false; }
}
