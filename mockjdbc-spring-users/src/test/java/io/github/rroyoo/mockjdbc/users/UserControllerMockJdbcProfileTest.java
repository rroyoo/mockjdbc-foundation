package io.github.rroyoo.mockjdbc.users;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import io.github.rroyoo.mockjdbc.users.testsupport.tcp.RequiresTcpService;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("mockjdbc")
@RequiresTcpService(
        name = "gRPC mock server",
        hostEnv = "MOCKJDBC_GRPC_HOST",
        portEnv = "MOCKJDBC_GRPC_PORT",
        host = "localhost",
        port = 8088,
        timeoutMs = 250
)
class UserControllerMockJdbcProfileTest {

    @Autowired
    private MockMvc mockMvc;

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
}
