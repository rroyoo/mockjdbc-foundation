package io.github.rroyoo.mockjdbc.mock.statement;

import io.github.rroyoo.mockjdbc.mock.driver.MockConfig;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

final class StatementQueryHandler {

    private final StatementLifecycleHandler lifecycle;
    private final GrpcMockQueryClient client;

    StatementQueryHandler(MockConfig mockConfig, StatementLifecycleHandler lifecycle) {
        this.lifecycle = lifecycle;
        this.client = new GrpcMockQueryClient(mockConfig);
    }

    public ResultSet executeQuery(String sql) throws SQLException {
        lifecycle.assertOpen();
        return ResultSetFactory.create(client.findResultSet(sql, List.of()));
    }
}

