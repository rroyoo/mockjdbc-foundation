package io.github.rroyoo.mockjdbc.mock.statement;

import io.github.rroyoo.mockjdbc.mock.driver.MockConfig;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

final class StatementQueryHandler {

    private final StatementLifecycleHandler lifecycle;
    private final StatementExecutionStateHandler executionState;
    private final GrpcMockQueryClient client;

    StatementQueryHandler(MockConfig mockConfig, StatementLifecycleHandler lifecycle, StatementExecutionStateHandler executionState) {
        this.lifecycle = lifecycle;
        this.executionState = executionState;
        this.client = new GrpcMockQueryClient(mockConfig);
    }

    public ResultSet executeQuery(String sql) throws SQLException {
        lifecycle.assertOpen();
        var resultSet = ResultSetFactory.create(client.findResultSet(sql, List.of()));
        executionState.storeResultSet(resultSet);
        return resultSet;
    }

    public boolean execute(String sql) throws SQLException {
        executeQuery(sql);
        return true;
    }

    public ResultSet getResultSet() throws SQLException {
        lifecycle.assertOpen();
        return executionState.getResultSet();
    }

    public int getUpdateCount() throws SQLException {
        lifecycle.assertOpen();
        return executionState.getUpdateCount();
    }

    public boolean getMoreResults() throws SQLException {
        lifecycle.assertOpen();
        return executionState.getMoreResults();
    }
}
