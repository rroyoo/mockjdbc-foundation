package io.github.rroyoo.mockjdbc.users;

import io.github.rroyoo.mockjdbc.mock.MockedQuery;
import io.github.rroyoo.mockjdbc.proxy.AsyncDispatchConfig;
import io.github.rroyoo.mockjdbc.proxy.JdbcAgentRegistry;
import io.github.rroyoo.mockjdbc.proxy.MockedQueryEventProducer;
import org.h2.tools.SimpleResultSet;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ParameterMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLNonTransientException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("h2")
public class UserControllerH2ProfileTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DataSource dataSource;

    @Test
    @DisplayName("Given h2 profile, when listing users, then seeded users are returned")
    void shouldListSeededUsersWithH2Profile() throws Exception {
        mockMvc.perform(get("/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Ada Lovelace"))
                .andExpect(jsonPath("$[1].name").value("Grace Hopper"));
    }

    @Test
    @DisplayName("Given h2 profile, when creating a user, then API returns 201 and persisted payload")
    void shouldCreateUserWithH2Profile() throws Exception {
        mockMvc.perform(post("/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Linus Torvalds\",\"email\":\"linus@mockjdbc.dev\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.name").value("Linus Torvalds"))
                .andExpect(jsonPath("$.email").value("linus@mockjdbc.dev"));
    }

    @Test
    @DisplayName("Given h2 record profile, when calling a procedure with parameters, then result and recorded event are returned")
    void shouldCallAndRecordProcedureWithParametersUsingH2Profile() throws Exception {
        jdbcTemplate.execute("CREATE ALIAS ADD_NUMBERS FOR "
                + "\"io.github.rroyoo.mockjdbc.users.UserControllerH2ProfileTest.addNumbers\"");

        var emittedQueries = new CopyOnWriteArrayList<MockedQuery>();
        var eventLatch = new CountDownLatch(1);
        try (var registration = JdbcAgentRegistry.register(
                dataSource,
                "users-h2-test",
                event -> {},
                MockedQueryEventProducer.fromConsumer(query -> {
                    emittedQueries.add(query);
                    eventLatch.countDown();
                }),
                AsyncDispatchConfig.defaults())) {
            Integer result = jdbcTemplate.execute((ConnectionCallback<Integer>) connection -> {
                try (var statement = JdbcAgentRegistry.wrapIfRegistered(dataSource, connection)
                        .prepareCall("{ call ADD_NUMBERS(?, ?) }")) {
                    statement.setInt(1, 19);
                    statement.setInt(2, 23);
                    try (var resultSet = statement.executeQuery()) {
                        assertTrue(resultSet.next());
                        return resultSet.getInt(1);
                    }
                }
            });

            assertEquals(42, result);
            assertTrue(eventLatch.await(5, TimeUnit.SECONDS), "Expected the procedure call to be recorded");
            var recordedQuery = emittedQueries.get(0);
            assertTrue(recordedQuery.hasCallableStatement());
            assertEquals("{ call ADD_NUMBERS(?, ?) }", recordedQuery.getCallableStatement().getCallSql());
            assertEquals(19L, recordedQuery.getCallableStatement().getParameters(0).getValue().getLongVal());
            assertEquals(23L, recordedQuery.getCallableStatement().getParameters(1).getValue().getLongVal());
        } finally {
            jdbcTemplate.execute("DROP ALIAS IF EXISTS ADD_NUMBERS");
        }
    }

    @Test
    @DisplayName("Given h2 record profile, when calling a function with an OUT parameter, then OUT value is recorded")
    void shouldCallAndRecordFunctionWithOutParameterUsingH2Profile() throws Exception {
        jdbcTemplate.execute("CREATE ALIAS ADD_SCALAR FOR "
                + "\"io.github.rroyoo.mockjdbc.users.UserControllerH2ProfileTest.addScalar\"");

        var emittedQueries = new CopyOnWriteArrayList<MockedQuery>();
        var eventLatch = new CountDownLatch(1);
        try (var registration = JdbcAgentRegistry.register(
                dataSource,
                "users-h2-test",
                event -> {},
                MockedQueryEventProducer.fromConsumer(query -> {
                    emittedQueries.add(query);
                    eventLatch.countDown();
                }),
                AsyncDispatchConfig.defaults())) {
            Integer result = jdbcTemplate.execute((ConnectionCallback<Integer>) connection -> {
                try (var statement = JdbcAgentRegistry.wrapIfRegistered(dataSource, connection)
                        .prepareCall("{ ? = call ADD_SCALAR(?, ?) }")) {
                    statement.registerOutParameter(1, Types.INTEGER);
                    statement.setInt(2, 19);
                    statement.setInt(3, 23);
                    statement.executeUpdate();
                    return statement.getInt(1);
                }
            });

            assertEquals(42, result);
            assertTrue(eventLatch.await(5, TimeUnit.SECONDS), "Expected the function call to be recorded");
            var parameters = emittedQueries.get(0).getCallableStatement().getParametersList();
            var outParameter = parameters.stream()
                    .filter(parameter -> parameter.getMode() == ParameterMetaData.parameterModeOut)
                    .findFirst()
                    .orElseThrow();
            assertEquals(1, outParameter.getIndex());
            assertEquals(42L, outParameter.getValue().getLongVal());
        } finally {
            jdbcTemplate.execute("DROP ALIAS IF EXISTS ADD_SCALAR");
        }
    }

    @Test
    @DisplayName("Given h2 record profile, when a procedure returns multiple or no rows, then both result sets are captured")
    void shouldCallProceduresReturningMultipleAndNoRowsUsingH2Profile() throws Exception {
        jdbcTemplate.execute("CREATE ALIAS MULTI_ROW_PROCEDURE FOR "
                + "\"io.github.rroyoo.mockjdbc.users.UserControllerH2ProfileTest.multipleRows\"");
        jdbcTemplate.execute("CREATE ALIAS EMPTY_PROCEDURE FOR "
                + "\"io.github.rroyoo.mockjdbc.users.UserControllerH2ProfileTest.noRows\"");

        var emittedQueries = new CopyOnWriteArrayList<MockedQuery>();
        var eventLatch = new CountDownLatch(2);
        try (var registration = JdbcAgentRegistry.register(
                dataSource,
                "users-h2-test",
                event -> {},
                MockedQueryEventProducer.fromConsumer(query -> {
                    emittedQueries.add(query);
                    eventLatch.countDown();
                }),
                AsyncDispatchConfig.defaults())) {
            var rowValues = jdbcTemplate.execute((ConnectionCallback<List<Integer>>) connection -> {
                try (var statement = JdbcAgentRegistry.wrapIfRegistered(dataSource, connection)
                        .prepareCall("{ call MULTI_ROW_PROCEDURE() }");
                     var resultSet = statement.executeQuery()) {
                    var values = new ArrayList<Integer>();
                    while (resultSet.next()) {
                        values.add(resultSet.getInt(1));
                    }
                    return values;
                }
            });
            var hasEmptyResult = jdbcTemplate.execute((ConnectionCallback<Boolean>) connection -> {
                try (var statement = JdbcAgentRegistry.wrapIfRegistered(dataSource, connection)
                        .prepareCall("{ call EMPTY_PROCEDURE() }");
                     var resultSet = statement.executeQuery()) {
                    return !resultSet.next();
                }
            });

            assertEquals(java.util.List.of(11, 22, 33), rowValues);
            assertTrue(hasEmptyResult);
            assertTrue(eventLatch.await(5, TimeUnit.SECONDS), "Expected both procedure calls to be recorded");
            assertEquals(2, emittedQueries.size());
            assertEquals(3, emittedQueries.get(0).getResultSet().getRowsCount());
            assertEquals(0, emittedQueries.get(1).getResultSet().getRowsCount());
        } finally {
            jdbcTemplate.execute("DROP ALIAS IF EXISTS MULTI_ROW_PROCEDURE");
            jdbcTemplate.execute("DROP ALIAS IF EXISTS EMPTY_PROCEDURE");
        }
    }

    @Test
    @DisplayName("Given h2 record profile, when a procedure throws SQLException, then the exception and captured error are preserved")
    void shouldCaptureProcedureSQLExceptionUsingH2Profile() throws Exception {
        jdbcTemplate.execute("CREATE ALIAS FAIL_PROCEDURE FOR "
                + "\"io.github.rroyoo.mockjdbc.users.UserControllerH2ProfileTest.failProcedure\"");

        var emittedQueries = new CopyOnWriteArrayList<MockedQuery>();
        var eventLatch = new CountDownLatch(1);
        try (var registration = JdbcAgentRegistry.register(
                dataSource,
                "users-h2-test",
                event -> {},
                MockedQueryEventProducer.fromConsumer(query -> {
                    emittedQueries.add(query);
                    eventLatch.countDown();
                }),
                AsyncDispatchConfig.defaults())) {
            SQLException exception;
            try (var connection = dataSource.getConnection();
                 var statement = JdbcAgentRegistry.wrapIfRegistered(dataSource, connection)
                         .prepareCall("{ call FAIL_PROCEDURE(?) }")) {
                statement.setInt(1, 999);
                exception = assertThrows(SQLException.class,
                        statement::executeQuery);
            }

            assertTrue(eventLatch.await(5, TimeUnit.SECONDS), "Expected the failed procedure call to be recorded");
            var recordedQuery = emittedQueries.get(0);
            assertEquals(exception.getClass().getName(), recordedQuery.getError().getType());
            assertEquals(exception.getMessage(), recordedQuery.getError().getMessage());
        } finally {
            jdbcTemplate.execute("DROP ALIAS IF EXISTS FAIL_PROCEDURE");
        }
    }

    public static int addScalar(int first, int second) {
        return first + second;
    }

    public static ResultSet addNumbers(Connection connection, int first, int second) throws SQLException {
        var resultSet = new SimpleResultSet();
        resultSet.addColumn("result", Types.INTEGER, 10, 0);
        resultSet.addRow(first + second);
        return resultSet;
    }

    public static ResultSet multipleRows() {
        var resultSet = new SimpleResultSet();
        resultSet.addColumn("result", Types.INTEGER, 10, 0);
        resultSet.addRow(11);
        resultSet.addRow(22);
        resultSet.addRow(33);
        return resultSet;
    }

    public static ResultSet noRows() {
        var resultSet = new SimpleResultSet();
        resultSet.addColumn("result", Types.INTEGER, 10, 0);
        return resultSet;
    }

    public static ResultSet failProcedure(Connection connection, int value) throws SQLNonTransientException {
        if (value == 999) {
            throw new SQLNonTransientException("procedure failed");
        }
        return new SimpleResultSet();
    }
}
