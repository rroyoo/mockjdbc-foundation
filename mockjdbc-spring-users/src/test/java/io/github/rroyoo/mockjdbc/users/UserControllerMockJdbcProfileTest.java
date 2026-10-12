package io.github.rroyoo.mockjdbc.users;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.ComposeContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.containers.wait.strategy.Wait;

import java.io.File;
import java.sql.ResultSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
}
