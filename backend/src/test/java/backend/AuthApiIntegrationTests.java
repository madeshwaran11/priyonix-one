package backend;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Date;
import java.util.Map;
import java.util.Optional;

import javax.crypto.SecretKey;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;

import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * End-to-end tests for the auth API through the real Spring Security
 * filter chain (JWT filter, CORS, authorization rules).
 *
 * Uses dummy secrets and mocks the Mongo layer so the suite is hermetic
 * and never touches the real Atlas cluster, no matter what environment
 * variables the developer shell has exported.
 */
@SpringBootTest(properties = {
        "spring.mongodb.uri=mongodb://127.0.0.1:27017/priyonix-test",
        "JWT_SECRET=dGVzdC1zZWNyZXQtZm9yLXVuaXQtYnVpbGRzLW9ubHktMzItYnl0ZXM=",
        // ADMIN_PASSWORD stays unset so AdminSetup never runs for real;
        // ADMIN_EMAIL/ADMIN_RESET_TOKEN only feed the reset-endpoint tests.
        "ADMIN_EMAIL=admin@example.com",
        "ADMIN_RESET_TOKEN=integration-test-reset-token"
})
@AutoConfigureMockMvc
class AuthApiIntegrationTests extends PlatformMockDatabaseTestSupport {

    private static final String TEST_JWT_SECRET =
            "dGVzdC1zZWNyZXQtZm9yLXVuaXQtYnVpbGRzLW9ubHktMzItYnl0ZXM=";

    private static final String RAW_PASSWORD = "Sup3rSecret-Passw0rd!";

    private static final String RESET_TOKEN = "integration-test-reset-token";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @MockitoBean
    private UserRepository userRepository;

    /**
     * Mocking the database factory (rather than MongoTemplate) keeps the
     * real MongoTemplate/GridFsTemplate/repositories intact for the
     * context while preventing any real network I/O. Deep stubs make
     * mongoTemplate.getDb().runCommand(ping) a no-op for the health test.
     */
    @MockitoBean(answers = Answers.RETURNS_DEEP_STUBS)
    private MongoDatabaseFactory mongoDatabaseFactory;

    /** Protected probe endpoint so authorization can be asserted. */
    @TestConfiguration
    static class ProbeConfig {
        @Bean
        ProbeController probeController() {
            return new ProbeController();
        }
    }

    @RestController
    static class ProbeController {
        @GetMapping("/api/admin-only")
        Map<String, String> probe() {
            return Map.of("status", "PROTECTED-OK");
        }
    }

    private User enabledAdmin() {
        User user = new User();
        user.setEmail("admin@example.com");
        user.setPassword(new PasswordConfig()
                .passwordEncoder().encode(RAW_PASSWORD));
        user.setRole("ADMIN");
        user.setEnabled(true);
        return user;
    }

    private SecretKey signingKey() {
        return Keys.hmacShaKeyFor(Decoders.BASE64.decode(TEST_JWT_SECRET));
    }

    private String expiredTokenFor(String email) {
        Date now = new Date();
        return Jwts.builder()
                .subject(email)
                .claim("role", "ADMIN")
                .issuedAt(new Date(now.getTime() - 10 * 60 * 1000L))
                .expiration(new Date(now.getTime() - 5 * 60 * 1000L))
                .signWith(signingKey())
                .compact();
    }

    private String validTokenFor(String email) {
        return Jwts.builder()
                .subject(email)
                .claim("role", "ADMIN")
                .issuedAt(new Date())
                .expiration(new Date(
                        System.currentTimeMillis() + 15 * 60 * 1000L))
                .signWith(signingKey())
                .compact();
    }

    private String loginJson(String email, String password) {
        return "{\"email\":\"" + email + "\",\"password\":\""
                + password + "\"}";
    }

    private String extractToken(String json) {
        String marker = "\"token\":\"";
        int start = json.indexOf(marker);
        org.junit.jupiter.api.Assertions.assertTrue(start >= 0,
                "token missing in response: " + json);
        start += marker.length();
        return json.substring(start, json.indexOf('"', start));
    }

    // ----- login ---------------------------------------------------------

    @Test
    void loginSucceedsWithValidCredentials() throws Exception {
        when(userRepository.findByEmailIgnoreCase("admin@example.com"))
                .thenReturn(Optional.of(enabledAdmin()));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson("admin@example.com",
                                RAW_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.email").value("admin@example.com"))
                .andExpect(jsonPath("$.role").value("ADMIN"))
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    void loginTokenCarriesCorrectClaims() throws Exception {
        when(userRepository.findByEmailIgnoreCase("admin@example.com"))
                .thenReturn(Optional.of(enabledAdmin()));

        String responseBody = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson("AdMiN@ExAmPlE.CoM",
                                RAW_PASSWORD)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        String token = extractToken(responseBody);

        Claims claims = jwtService.validateToken(token);
        org.junit.jupiter.api.Assertions.assertEquals(
                "admin@example.com", claims.getSubject());
        org.junit.jupiter.api.Assertions.assertEquals(
                "ADMIN", claims.get("role", String.class));
        org.junit.jupiter.api.Assertions.assertNotNull(claims.getIssuedAt());
        org.junit.jupiter.api.Assertions.assertNotNull(claims.getExpiration());
    }

    @Test
    void loginFailsWithIncorrectPassword() throws Exception {
        when(userRepository.findByEmailIgnoreCase("admin@example.com"))
                .thenReturn(Optional.of(enabledAdmin()));

        String body = loginJson("admin@example.com", "wrong-password");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value("ERROR"))
                .andExpect(jsonPath("$.message")
                        .value("Invalid email or password"));
    }

    @Test
    void loginFailsForMissingUser() throws Exception {
        when(userRepository.findByEmailIgnoreCase("ghost@example.com"))
                .thenReturn(Optional.empty());

        String body = loginJson("ghost@example.com", RAW_PASSWORD);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value("ERROR"));
    }

    @Test
    void loginFailsForDisabledUser() throws Exception {
        User disabled = enabledAdmin();
        disabled.setEnabled(false);
        when(userRepository.findByEmailIgnoreCase("admin@example.com"))
                .thenReturn(Optional.of(disabled));

        String body = loginJson("admin@example.com", RAW_PASSWORD);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value("ERROR"));
    }

    // ----- security chain ------------------------------------------------

    @Test
    void protectedEndpointRejectsAnonymousRequest() throws Exception {
        mockMvc.perform(get("/api/admin-only"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value("ERROR"))
                .andExpect(jsonPath("$.message").value("Unauthorized"));
    }

    @Test
    void protectedEndpointAcceptsValidToken() throws Exception {
        when(userRepository.findByEmailIgnoreCase("admin@example.com"))
                .thenReturn(Optional.of(enabledAdmin()));

        mockMvc.perform(get("/api/admin-only")
                        .header("Authorization",
                                "Bearer " + validTokenFor("admin@example.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PROTECTED-OK"));
    }

    @Test
    void protectedEndpointRejectsExpiredToken() throws Exception {
        when(userRepository.findByEmailIgnoreCase("admin@example.com"))
                .thenReturn(Optional.of(enabledAdmin()));

        mockMvc.perform(get("/api/admin-only")
                        .header("Authorization",
                                "Bearer "
                                + expiredTokenFor("admin@example.com")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message")
                        .value("Invalid or expired token"));
    }

    @Test
    void protectedEndpointRejectsMalformedToken() throws Exception {
        mockMvc.perform(get("/api/admin-only")
                        .header("Authorization", "Bearer not.a-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message")
                        .value("Invalid or expired token"));
    }

    @Test
    void protectedEndpointRejectsTokenForUnknownUser() throws Exception {
        when(userRepository.findByEmailIgnoreCase("admin@example.com"))
                .thenReturn(Optional.empty());

        mockMvc.perform(get("/api/admin-only")
                        .header("Authorization",
                                "Bearer " + validTokenFor("admin@example.com")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void protectedEndpointRejectsTokenForDisabledUser() throws Exception {
        User disabled = enabledAdmin();
        disabled.setEnabled(false);
        when(userRepository.findByEmailIgnoreCase("admin@example.com"))
                .thenReturn(Optional.of(disabled));

        mockMvc.perform(get("/api/admin-only")
                        .header("Authorization",
                                "Bearer " + validTokenFor("admin@example.com")))
                .andExpect(status().isUnauthorized());
    }

    // ----- role-based access control ------------------------------------

    private User enabledUser(String email) {
        User user = enabledAdmin();
        user.setEmail(email);
        user.setRole("USER");
        return user;
    }

    @Test
    void userRoleCannotDeleteEmployees() throws Exception {
        when(userRepository.findByEmailIgnoreCase("user@example.com"))
                .thenReturn(Optional.of(enabledUser("user@example.com")));

        mockMvc.perform(delete("/api/employees/missing")
                        .header("Authorization",
                                "Bearer " + validTokenFor("user@example.com")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value("ERROR"))
                .andExpect(jsonPath("$.message").value("Forbidden"));
    }

    @Test
    void userRoleCannotApproveExpenses() throws Exception {
        when(userRepository.findByEmailIgnoreCase("user@example.com"))
                .thenReturn(Optional.of(enabledUser("user@example.com")));

        mockMvc.perform(patch("/api/expenses/missing/status")
                        .header("Authorization",
                                "Bearer " + validTokenFor("user@example.com"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"APPROVED\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Forbidden"));
    }

    @Test
    void userRoleCanReadBusinessData() throws Exception {
        when(userRepository.findByEmailIgnoreCase("user@example.com"))
                .thenReturn(Optional.of(enabledUser("user@example.com")));

        // The probe endpoint stands in for any authenticated GET route.
        mockMvc.perform(get("/api/admin-only")
                        .header("Authorization",
                                "Bearer " + validTokenFor("user@example.com")))
                .andExpect(status().isOk());
    }

    // ----- public endpoints / CORS --------------------------------------

    @Test
    void healthEndpointIsPublicAndReportsUp() throws Exception {
        // mongoDatabaseFactory is a deep-stub mock, so the ping is a no-op.
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.application")
                        .value("PriyoniX One"));
    }

    @Test
    void corsPreflightFromReactDevServerIsAccepted() throws Exception {
        mockMvc.perform(options("/api/auth/login")
                        .header(HttpHeaders.ORIGIN,
                                "http://localhost:5173")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD,
                                "POST")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS,
                                "content-type"))
                .andExpect(status().isOk())
                .andExpect(header().string(
                        "Access-Control-Allow-Origin",
                        "http://localhost:5173"));
    }

    @Test
    void loginAcceptsPlainContentTypePreflight() throws Exception {
        when(userRepository.findByEmailIgnoreCase("admin@example.com"))
                .thenReturn(Optional.of(enabledAdmin()));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"admin@example.com\","
                                + "\"password\":\"" + RAW_PASSWORD + "\"}"))
                .andExpect(status().isOk());
    }

    // ----- admin reset endpoint -----------------------------------------

    @Test
    void resetRejectsMissingTokenHeader() throws Exception {
        mockMvc.perform(post("/api/auth/admin/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson("admin@example.com",
                                "NewStrongPassword123!")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value("ERROR"));
    }

    @Test
    void resetRejectsWrongToken() throws Exception {
        mockMvc.perform(post("/api/auth/admin/reset-password")
                        .header("X-Admin-Reset-Token", "wrong-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson("admin@example.com",
                                "NewStrongPassword123!")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value("ERROR"));
    }

    @Test
    void resetRejectsNonAdminEmail() throws Exception {
        mockMvc.perform(post("/api/auth/admin/reset-password")
                        .header("X-Admin-Reset-Token", RESET_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson("someone-else@example.com",
                                "NewStrongPassword123!")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("ERROR"));
    }

    @Test
    void resetRejectsShortPassword() throws Exception {
        mockMvc.perform(post("/api/auth/admin/reset-password")
                        .header("X-Admin-Reset-Token", RESET_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson("admin@example.com", "short")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("ERROR"));
    }

    @Test
    void resetSucceedsWithConfiguredToken() throws Exception {
        when(userRepository.save(any(User.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        mockMvc.perform(post("/api/auth/admin/reset-password")
                        .header("X-Admin-Reset-Token", RESET_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson("ADMIN@Example.com",
                                "NewStrongPassword123!")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"));

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());

        User saved = captor.getValue();
        org.junit.jupiter.api.Assertions.assertEquals(
                "admin@example.com", saved.getEmail());
        org.junit.jupiter.api.Assertions.assertEquals(
                "ADMIN", saved.getRole());
        org.junit.jupiter.api.Assertions.assertTrue(saved.isEnabled());
        org.junit.jupiter.api.Assertions.assertTrue(new PasswordConfig()
                .passwordEncoder().matches(
                        "NewStrongPassword123!", saved.getPassword()));
    }
}
