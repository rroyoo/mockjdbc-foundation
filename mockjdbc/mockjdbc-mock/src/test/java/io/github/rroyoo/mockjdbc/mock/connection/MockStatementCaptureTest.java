package io.github.rroyoo.mockjdbc.mock.connection;

import io.github.rroyoo.mockjdbc.mock.CallableStatement;
import io.github.rroyoo.mockjdbc.mock.MockedQuery;
import io.github.rroyoo.mockjdbc.mock.PlainStatement;
import io.github.rroyoo.mockjdbc.mock.PreparedStatement;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.sql.Statement;
import java.util.List;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MockStatementCaptureTest {

    @ParameterizedTest(name = "captures plain executeQuery for SQL: {0}")
    @ValueSource(strings = {
        "select 1",
        "select * from customers where id = 9"
    })
    @DisplayName("captures plain statement executeQuery invocations")
    void capturesPlainStatementQueries(String sql) throws Exception {
        MockConnection connection = new MockConnection(new Properties(), new NoOpQueryServiceAdapter());

        Statement statement = connection.createStatement();
        statement.executeQuery(sql);

        List<CapturedQuery> capturedQueries = connection.getCapturedQueries();
        assertEquals(1, capturedQueries.size());
        assertEquals(sql, capturedQueries.get(0).sql());
        assertEquals(CapturedQuery.StatementType.PLAIN, capturedQueries.get(0).statementType());
        assertEquals(List.of(), capturedQueries.get(0).orderedParams());
    }

    @Test
    @DisplayName("captures prepared executeQuery with deterministic parameter order")
    void capturesPreparedQueryWithDeterministicOrder() throws Exception {
        MockConnection connection = new MockConnection(new Properties(), new NoOpQueryServiceAdapter());

        java.sql.PreparedStatement statement = connection.prepareStatement("select * from accounts where id = ? and owner = ?");
        statement.setString(2, "alice");
        statement.setInt(1, 7);
        statement.executeQuery();

        List<CapturedQuery> capturedQueries = connection.getCapturedQueries();
        assertEquals(1, capturedQueries.size());
        assertEquals(CapturedQuery.StatementType.PREPARED, capturedQueries.get(0).statementType());
        assertEquals(List.of(7, "alice"), capturedQueries.get(0).orderedParams());
    }

    @Test
    @DisplayName("returned capture list and params are immutable and clear removes all captures")
    void capturedQueriesAreDefensiveAndClearable() throws Exception {
        MockConnection connection = new MockConnection(new Properties(), new NoOpQueryServiceAdapter());

        java.sql.PreparedStatement statement = connection.prepareStatement("select * from products where id = ?");
        statement.setInt(1, 5);
        statement.executeQuery();

        List<CapturedQuery> capturedQueries = connection.getCapturedQueries();
        assertThrows(UnsupportedOperationException.class, () -> capturedQueries.add(CapturedQuery.plain("select 2")));
        assertThrows(UnsupportedOperationException.class, () -> capturedQueries.get(0).orderedParams().add(9));

        assertEquals(1, connection.countCapturedQueries());
        connection.clearCapturedQueries();
        assertEquals(0, connection.countCapturedQueries());
    }

    private static final class NoOpQueryServiceAdapter implements QueryServiceAdapter {
        @Override
        public MockedQuery findMockedQuery(PlainStatement plainStatement) {
            return null;
        }

        @Override
        public MockedQuery findMockedQuery(PreparedStatement preparedStatement) {
            return null;
        }

        @Override
        public MockedQuery findMockedQuery(CallableStatement callableStatement) {
            return null;
        }

        @Override
        public void close() {
        }
    }
}

