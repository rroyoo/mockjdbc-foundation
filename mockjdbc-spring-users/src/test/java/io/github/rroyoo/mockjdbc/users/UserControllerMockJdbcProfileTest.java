package io.github.rroyoo.mockjdbc.users;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.ComposeContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.containers.wait.strategy.Wait;

import javax.sql.DataSource;
import java.io.File;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLNonTransientException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("mockjdbc")
@Testcontainers(disabledWithoutDocker = true)
class UserControllerMockJdbcProfileTest {

    private static final int WIREMOCK_PORT = 8080;

    @Container
    private static final ComposeContainer MOCK_BACKEND =
            new ComposeContainer(new File("docker-compose.yml"))
                    .withLocalCompose(true)
                    .withExposedService("wiremock-grpc-1", WIREMOCK_PORT,
                            Wait.forHttp("/__admin").forStatusCode(200));

    @DynamicPropertySource
    static void configureMockServer(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:mock://"
                + MOCK_BACKEND.getServiceHost("wiremock-grpc-1", WIREMOCK_PORT)
                + ":" + MOCK_BACKEND.getServicePort("wiremock-grpc-1", WIREMOCK_PORT));
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DataSource dataSource;

    @Test
    @DisplayName("Given mockjdbc profile, when listing users, then mocked gRPC response is returned")
    void shouldListUsersUsingMockJdbcProfile() throws Exception {
        mockMvc.perform(get("/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Ada Lovelace"))
                .andExpect(jsonPath("$[1].name").value("Grace Hopper"));
    }

    @Test
    @DisplayName("Given mockjdbc profile, when creating a user, then API returns data from mocked backend")
    void shouldCreateUserUsingMockJdbcProfile() throws Exception {
        mockMvc.perform(post("/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Donald Knuth\",\"email\":\"knuth@mockjdbc.dev\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.name").value("Donald Knuth"))
                .andExpect(jsonPath("$.email").value("knuth@mockjdbc.dev"));

        mockMvc.perform(get("/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[2].name").value("Donald Knuth"));
    }

    @Test
    @DisplayName("Given mockjdbc profile, when calling a procedure with parameters, then mocked result is returned")
    void shouldCallProcedureWithParametersUsingMockJdbcProfile() {
        Integer result = jdbcTemplate.execute((ConnectionCallback<Integer>) connection -> {
            try (var statement = connection.prepareCall("{ call ADD_NUMBERS(?, ?) }")) {
                statement.setInt(1, 19);
                statement.setInt(2, 23);
                try (ResultSet resultSet = statement.executeQuery()) {
                    assertTrue(resultSet.next());
                    return resultSet.getInt(1);
                }
            }
        });

        assertEquals(42, result);
    }

    @Test
    @DisplayName("Given mockjdbc profile, when calling a function with an OUT parameter, then mocked OUT value is returned")
    void shouldReadOutParameterUsingMockJdbcProfile() {
        Integer result = jdbcTemplate.execute((ConnectionCallback<Integer>) connection -> {
            try (var statement = connection.prepareCall("{ ? = call ADD_SCALAR(?, ?) }")) {
                statement.registerOutParameter(1, Types.INTEGER);
                statement.setInt(2, 19);
                statement.setInt(3, 23);
                statement.execute();
                return statement.getInt(1);
            }
        });

        assertEquals(42, result);
    }

    @Test
    @DisplayName("Given mockjdbc profile, when a procedure returns multiple or no rows, then the mocked rows are returned")
    void shouldReadProceduresReturningMultipleAndNoRowsUsingMockJdbcProfile() {
        var rowValues = jdbcTemplate.execute((ConnectionCallback<List<Integer>>) connection -> {
            try (var statement = connection.prepareCall("{ call MULTI_ROW_PROCEDURE() }");
                 var resultSet = statement.executeQuery()) {
                var values = new ArrayList<Integer>();
                while (resultSet.next()) {
                    values.add(resultSet.getInt(1));
                }
                return values;
            }
        });
        var hasEmptyResult = jdbcTemplate.execute((ConnectionCallback<Boolean>) connection -> {
            try (var statement = connection.prepareCall("{ call EMPTY_PROCEDURE() }");
                 var resultSet = statement.executeQuery()) {
                return !resultSet.next();
            }
        });

        assertEquals(java.util.List.of(11, 22, 33), rowValues);
        assertTrue(hasEmptyResult);
    }

    @Test
    @DisplayName("Given mockjdbc profile, when a procedure response contains SQLException, then its type and message are returned")
    void shouldThrowProcedureSQLExceptionUsingMockJdbcProfile() throws SQLException {
        try (var connection = dataSource.getConnection();
             var statement = connection.prepareCall("{ call FAIL_PROCEDURE(?) }")) {
            statement.setInt(1, 999);
            var exception = assertThrows(SQLNonTransientException.class,
                    statement::executeQuery);
            assertEquals("procedure failed", exception.getMessage());
        }
    }
}
