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
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

    public static ResultSet addNumbers(Connection connection, int first, int second) throws SQLException {
        var resultSet = new SimpleResultSet();
        resultSet.addColumn("result", Types.INTEGER, 10, 0);
        resultSet.addRow(first + second);
        return resultSet;
    }
}
