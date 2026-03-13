package io.github.rroyoo.mockjdbc.mock.statement;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

final class StatementExecutionStateHandler {

    private final AtomicReference<ResultSet> currentResultSet = new AtomicReference<>(null);
    private final AtomicInteger updateCount = new AtomicInteger(-1);
    private final AtomicLong largeUpdateCount = new AtomicLong(-1L);
    private final AtomicBoolean closeOnCompletion = new AtomicBoolean(false);

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
        return getMoreResults(java.sql.Statement.CLOSE_CURRENT_RESULT);
    }

    public boolean getMoreResults(int current) throws SQLException {
        switch (current) {
            case java.sql.Statement.KEEP_CURRENT_RESULT -> {
                return false;
            }
            case java.sql.Statement.CLOSE_CURRENT_RESULT,
                 java.sql.Statement.CLOSE_ALL_RESULTS -> {
                closeCurrentResultSet();
                return false;
            }
            default -> throw new SQLException("Invalid getMoreResults flag: " + current);
        }
    }

    public void closeOnCompletion() throws SQLException {
        closeOnCompletion.set(true);
    }

    public boolean isCloseOnCompletion() throws SQLException {
        return closeOnCompletion.get();
    }

    public void closeCurrentResultSet() throws SQLException {
        var existing = currentResultSet.getAndSet(null);
        if (existing != null) {
            try {
                existing.close();
            } catch (SQLException ignored) {
                // Some ResultSet implementations do not support close/isClosed checks consistently.
            }
        }
    }
}
