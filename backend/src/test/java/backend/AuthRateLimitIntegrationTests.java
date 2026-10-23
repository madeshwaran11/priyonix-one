
package backend;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Verifies that the authentication endpoints slow down brute force attacks.
 *
 * The limits are lowered for this suite; the per-IP limits stay high so the
 * per-email behaviour can be asserted in isolation.
 */
@SpringBootTest(properties = {
        "spring.mongodb.uri=mongodb://127.0.0.1:27017/priyonix-test",
        "JWT_SECRET=dGVzdC1zZWNyZXQtZm9yLXVuaXQtYnVpbGRzLW9ubHktMzItYnl0ZXM=",
        "ADMIN_EMAIL=",
        "ADMIN_PASSWORD=",
        "PASSWORD_RESET_DEV_MODE=true",
        "AUTH_LOGIN_MAX_ATTEMPTS=3",
        "AUTH_LOGIN_MAX_IP_ATTEMPTS=1000",
        "AUTH_FORGOT_MAX_REQUESTS=2",
        "AUTH_FORGOT_MAX_IP_REQUESTS=1000",
        "AUTH_RESET_MAX_ATTEMPTS=2",
        "AUTH_RESET_WINDOW_MINUTES=15"
})
@AutoConfigureMockMvc
class AuthRateLimitIntegrationTests extends PlatformMockDatabaseTestSupport {

    private static final String RAW_PASSWORD = "Sup3rSecret-Passw0rd!";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private PasswordResetTokenRepository tokenRepository;

    @MockitoBean(answers = Answers.RETURNS_DEEP_STUBS)
    private MongoDatabaseFactory mongoDatabaseFactory;

    private User enabledEmployee() {
        User user = new User();
        user.setId("employee-1");
        user.setEmail("limited@example.com");
        user.setPassword(new PasswordConfig()
                .passwordEncoder().encode(RAW_PASSWORD));
        user.setRole("EMPLOYEE");
        user.setEnabled(true);
        return user;
    }

    @Test
    void repeatedFailedLoginsAreBlocked() throws Exception {
        when(userRepository.findByEmailIgnoreCase("limited@example.com"))
                .thenReturn(Optional.of(enabledEmployee()));

        for (int attempt = 0; attempt < 3; attempt++) {
            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"email\":\"limited@example.com\","
                                    + "\"password\":\"wrong-password\"}"))
                    .andExpect(status().isUnauthorized());
        }

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"limited@example.com\","
                                + "\"password\":\"wrong-password\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.status").value("ERROR"));
    }

    @Test
    void aSuccessfulLoginClearsTheFailureCounter() throws Exception {
        when(userRepository.findByEmailIgnoreCase("recovered@example.com"))
                .thenReturn(Optional.of(enabledEmployee()));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"recovered@example.com\","
                                + "\"password\":\"wrong-password\"}"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"recovered@example.com\","
                                + "\"password\":\"" + RAW_PASSWORD + "\"}"))
                .andExpect(status().isOk());

        // The counter was cleared, so a later failure is still answered with
        // a normal 401 instead of a 429.
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"recovered@example.com\","
                                + "\"password\":\"wrong-password\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void repeatedPasswordResetRequestsAreBlocked() throws Exception {
        when(userRepository.findByEmailIgnoreCase("limited@example.com"))
                .thenReturn(Optional.of(enabledEmployee()));
        when(tokenRepository.save(any(PasswordResetToken.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        for (int attempt = 0; attempt < 2; attempt++) {
            mockMvc.perform(post("/api/auth/forgot-password")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"email\":\"limited@example.com\"}"))
                    .andExpect(status().isOk());
        }

        mockMvc.perform(post("/api/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"limited@example.com\"}"))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void theRateLimitDoesNotRevealWhetherTheAccountExists() throws Exception {
        when(userRepository.findByEmailIgnoreCase("probe@example.com"))
                .thenReturn(Optional.of(enabledEmployee()));
        when(tokenRepository.save(any(PasswordResetToken.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        for (int attempt = 0; attempt < 2; attempt++) {
            mockMvc.perform(post("/api/auth/forgot-password")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"email\":\"probe@example.com\"}"))
                    .andExpect(status().isOk());
        }

        // The limit is applied per submitted address, so it also blocks an
        // address that does not exist at all. The limiter therefore never
        // discloses whether an account is present.
        for (int attempt = 0; attempt < 2; attempt++) {
            mockMvc.perform(post("/api/auth/forgot-password")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"email\":\"ghost@example.com\"}"))
                    .andExpect(status().isOk());
        }

        mockMvc.perform(post("/api/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"ghost@example.com\"}"))
                .andExpect(status().isTooManyRequests());

        mockMvc.perform(post("/api/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"probe@example.com\"}"))
                .andExpect(status().isTooManyRequests());
    }
}
