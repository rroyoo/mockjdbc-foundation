package io.github.rroyoo.mockjdbc.mock.statement;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

final class StatementExecutionStateHandler {

    private final AtomicReference<ResultSet> currentResultSet = new AtomicReference<>(null);
    private final AtomicInteger updateCount = new AtomicInteger(-1);

    public void storeResultSet(ResultSet resultSet) throws SQLException {
        closeCurrentResultSet();
        currentResultSet.set(resultSet);
        updateCount.set(-1);
    }

    public void storeUpdateCount(int count) throws SQLException {
        closeCurrentResultSet();
        updateCount.set(count);
    }

    public ResultSet getResultSet() throws SQLException {
        return currentResultSet.get();
    }

    public int getUpdateCount() throws SQLException {
        return updateCount.get();
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
