package io.github.rroyoo.mockjdbc.mock.connection;

import io.github.rroyoo.mockjdbc.mock.CallableStatement;
import io.github.rroyoo.mockjdbc.mock.MockedQuery;
import io.github.rroyoo.mockjdbc.mock.PlainStatement;
import io.github.rroyoo.mockjdbc.mock.PreparedStatement;

import java.sql.SQLException;

interface MockQueryServiceAdapter extends AutoCloseable {

    MockedQuery findMockedQuery(PlainStatement plainStatement) throws SQLException;

    MockedQuery findMockedQuery(PreparedStatement preparedStatement) throws SQLException;

    MockedQuery findMockedQuery(CallableStatement callableStatement) throws SQLException;
}
