package io.github.rroyoo.mockjdbc.mock.statement;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

final class StatementConfigHandler {

    private final AtomicInteger maxRows = new AtomicInteger(0);
    private final AtomicLong largeMaxRows = new AtomicLong(0L);
    private final AtomicInteger queryTimeout = new AtomicInteger(0);
    private final AtomicInteger fetchSize = new AtomicInteger(0);
    private final AtomicInteger fetchDirection = new AtomicInteger(ResultSet.FETCH_FORWARD);

    public void setMaxRows(int max) throws SQLException {
        maxRows.set(max);
    }

    public int getMaxRows() throws SQLException {
        return maxRows.get();
    }

    public void setLargeMaxRows(long max) throws SQLException {
        largeMaxRows.set(max);
    }

    public long getLargeMaxRows() throws SQLException {
        return largeMaxRows.get();
    }

    public void setQueryTimeout(int seconds) throws SQLException {
        queryTimeout.set(seconds);
    }

    public int getQueryTimeout() throws SQLException {
        return queryTimeout.get();
    }

    public void setFetchSize(int rows) throws SQLException {
        fetchSize.set(rows);
    }

    public int getFetchSize() throws SQLException {
        return fetchSize.get();
    }

    public void setFetchDirection(int direction) throws SQLException {
        fetchDirection.set(direction);
    }

    public int getFetchDirection() throws SQLException {
        return fetchDirection.get();
    }

    public int getResultSetType() throws SQLException {
        return ResultSet.TYPE_FORWARD_ONLY;
    }

    public int getResultSetConcurrency() throws SQLException {
        return ResultSet.CONCUR_READ_ONLY;
    }

    public int getResultSetHoldability() throws SQLException {
        return ResultSet.CLOSE_CURSORS_AT_COMMIT;
    }
}

