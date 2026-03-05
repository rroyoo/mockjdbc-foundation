package io.github.rroyoo.mockjdbc.mock.connection;

import io.github.rroyoo.mockjdbc.mock.MockedQuery;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MockQueryCaptureTest {

    @ParameterizedTest
    @ValueSource(strings = {
        "SELECT 1",
        "select * from users where status = 'ACTIVE'"
    })
    @DisplayName("should capture plain statement executeQuery sql")
    void shouldCapturePlainStatementExecuteQuerySql(String sql) throws Exception {
        MockConnection connection = new MockConnection(new Properties(), new NoOpQueryServiceAdapter());
        Statement statement = connection.createStatement();

        ResultSet resultSet = statement.executeQuery(sql);

        assertNull(resultSet);
        assertEquals(1, connection.getCapturedQueryCount());

        CapturedQuery capturedQuery = connection.getCapturedQueries().get(0);
        assertEquals(sql, capturedQuery.sql());
        assertEquals(CapturedQuery.StatementType.PLAIN, capturedQuery.statementType());
        assertEquals(List.of(), capturedQuery.orderedParams());
        assertNotNull(capturedQuery.timestamp());
    }

    @Test
    @DisplayName("should capture prepared statement query with ordered parameters")
    void shouldCapturePreparedStatementQueryWithOrderedParameters() throws Exception {
        MockConnection connection = new MockConnection(new Properties(), new NoOpQueryServiceAdapter());
        var preparedStatement = connection.prepareStatement("SELECT * FROM users WHERE id = ? AND name = ?");

        preparedStatement.setString(2, "alice");
        preparedStatement.setInt(1, 7);
        preparedStatement.executeQuery();

        assertEquals(1, connection.getCapturedQueryCount());

        CapturedQuery capturedQuery = connection.getCapturedQueries().get(0);
        assertEquals("SELECT * FROM users WHERE id = ? AND name = ?", capturedQuery.sql());
        assertEquals(CapturedQuery.StatementType.PREPARED, capturedQuery.statementType());
        assertEquals(List.of(7, "alice"), capturedQuery.orderedParams());
    }

    @Test
    @DisplayName("should return immutable capture list and clear state")
    void shouldReturnImmutableCaptureListAndClearState() throws Exception {
        MockConnection connection = new MockConnection(new Properties(), new NoOpQueryServiceAdapter());
        connection.createStatement().executeQuery("SELECT NOW()");

        List<CapturedQuery> capturedQueries = connection.getCapturedQueries();
        assertThrows(UnsupportedOperationException.class, () -> capturedQueries.add(capturedQueries.get(0)));

        connection.clearCapturedQueries();

        assertEquals(0, connection.getCapturedQueryCount());
        assertEquals(List.of(), connection.getCapturedQueries());
    }

    private static final class NoOpQueryServiceAdapter implements QueryServiceAdapter {

        @Override
        public MockedQuery findMockedQuery(io.github.rroyoo.mockjdbc.mock.PlainStatement plainStatement) {
            return null;
        }

        @Override
        public MockedQuery findMockedQuery(io.github.rroyoo.mockjdbc.mock.PreparedStatement preparedStatement) {
            return null;
        }

        @Override
        public MockedQuery findMockedQuery(io.github.rroyoo.mockjdbc.mock.CallableStatement callableStatement) {
            return null;
        }

        @Override
        public void close() {
        }
    }
}
