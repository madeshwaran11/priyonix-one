package backend;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The admin reset endpoint must be completely inert when the operator has
 * not configured ADMIN_RESET_TOKEN, regardless of what token a caller
 * sends.
 */
@SpringBootTest(properties = {
        "spring.mongodb.uri=mongodb://127.0.0.1:27017/priyonix-test",
        "JWT_SECRET=dGVzdC1zZWNyZXQtZm9yLXVuaXQtYnVpbGRzLW9ubHktMzItYnl0ZXM=",
        "ADMIN_EMAIL=admin@example.com",
        "ADMIN_PASSWORD="
})
@AutoConfigureMockMvc
class AdminResetDisabledIntegrationTests extends PlatformMockDatabaseTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void resetEndpointAlwaysUnauthorizedWhenTokenNotConfigured()
            throws Exception {
        mockMvc.perform(post("/api/auth/admin/reset-password")
                        .header("X-Admin-Reset-Token", "any-token-at-all")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"admin@example.com\","
                                + "\"password\":\"NewStrongPassword123!\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value("ERROR"));
    }

    @Test
    void resetEndpointUnauthorizedWithoutAnyHeader() throws Exception {
        mockMvc.perform(post("/api/auth/admin/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"admin@example.com\","
                                + "\"password\":\"NewStrongPassword123!\"}"))
                .andExpect(status().isUnauthorized());
    }
}
