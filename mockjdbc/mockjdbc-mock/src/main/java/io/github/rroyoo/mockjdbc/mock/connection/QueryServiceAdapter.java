package io.github.rroyoo.mockjdbc.mock.connection;

import io.github.rroyoo.mockjdbc.mock.CallableStatement;
import io.github.rroyoo.mockjdbc.mock.MockedQuery;
import io.github.rroyoo.mockjdbc.mock.PlainStatement;
import io.github.rroyoo.mockjdbc.mock.PreparedStatement;

import java.sql.SQLException;

/**
 * Service adapter for resolving mocked queries.
 *
 * This interface defines the contract for looking up mocked query definitions
 * based on statement type and parameters. Implementations may use different
 * transport mechanisms (e.g., gRPC, REST, in-memory).
 */
interface QueryServiceAdapter extends AutoCloseable {

    /**
     * Find mocked query definition for a plain SQL statement.
     */
    MockedQuery findMockedQuery(PlainStatement plainStatement) throws SQLException;

    /**
     * Find mocked query definition for a prepared statement.
     */
    MockedQuery findMockedQuery(PreparedStatement preparedStatement) throws SQLException;

    /**
     * Find mocked query definition for a callable statement.
     */
    MockedQuery findMockedQuery(CallableStatement callableStatement) throws SQLException;
}
