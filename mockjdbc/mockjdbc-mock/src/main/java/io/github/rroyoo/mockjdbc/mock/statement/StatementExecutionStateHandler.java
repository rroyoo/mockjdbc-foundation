package io.github.rroyoo.mockjdbc.mock.statement;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

final class StatementExecutionStateHandler {

    private final AtomicReference<ResultSet> currentResultSet = new AtomicReference<>(null);
    private final AtomicInteger updateCount = new AtomicInteger(-1);
    private final AtomicLong largeUpdateCount = new AtomicLong(-1L);

    public void storeResultSet(ResultSet resultSet) throws SQLException {
        closeCurrentResultSet();
        currentResultSet.set(resultSet);
        updateCount.set(-1);
        largeUpdateCount.set(-1L);
    }

    public void storeUpdateCount(int count) throws SQLException {
        closeCurrentResultSet();
        updateCount.set(count);
        largeUpdateCount.set(count);
    }

    public void storeLargeUpdateCount(long count) throws SQLException {
        closeCurrentResultSet();
        largeUpdateCount.set(count);
        updateCount.set(count > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) count);
    }

    public ResultSet getResultSet() throws SQLException {
        return currentResultSet.get();
    }

    public int getUpdateCount() throws SQLException {
        return updateCount.get();
    }

    public long getLargeUpdateCount() throws SQLException {
        return largeUpdateCount.get();
    }

    public boolean getMoreResults() throws SQLException {
        return false;
    }

    public void closeCurrentResultSet() throws SQLException {
        var existing = currentResultSet.getAndSet(null);
        if (existing != null && !existing.isClosed()) {
            existing.close();
        }
    }
}
