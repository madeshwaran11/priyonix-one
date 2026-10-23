
package backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Date;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;

import javax.crypto.SecretKey;

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
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * End-to-end tests for the two role based sign-in options and the complete
 * forgot/reset password flow.
 *
 * The suite runs against the real Spring Security filter chain and the real
 * controllers and services. Only the MongoDB layer is mocked, so the tests
 * never touch the Atlas cluster.
 */
@SpringBootTest(properties = {
        "spring.mongodb.uri=mongodb://127.0.0.1:27017/priyonix-test",
        "JWT_SECRET=dGVzdC1zZWNyZXQtZm9yLXVuaXQtYnVpbGRzLW9ubHktMzItYnl0ZXM=",
        // Keep the seeded admin out of the way.
        "ADMIN_EMAIL=",
        "ADMIN_PASSWORD=",
        // Development delivery so the forgot-password endpoint can be
        // exercised without an SMTP server.
        "PASSWORD_RESET_DEV_MODE=true",
        // The per-IP limits are covered by AuthRateLimitIntegrationTests;
        // here they are raised so the per-email behaviour stays isolated.
        "AUTH_LOGIN_MAX_IP_ATTEMPTS=1000",
        "AUTH_FORGOT_MAX_IP_REQUESTS=1000",
        "AUTH_RESET_MAX_ATTEMPTS=1000"
})
@AutoConfigureMockMvc
class AuthRoleApiIntegrationTests extends PlatformMockDatabaseTestSupport {

    private static final String TEST_JWT_SECRET =
            "dGVzdC1zZWNyZXQtZm9yLXVuaXQtYnVpbGRzLW9ubHktMzItYnl0ZXM=";

    private static final String RAW_PASSWORD = "Sup3rSecret-Passw0rd!";
    private static final String NEW_PASSWORD = "N3wStrong-Passw0rd!";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private PasswordResetTokenRepository tokenRepository;

    @MockitoBean
    private MetricsService metricsService;

    @MockitoBean
    private EmployeeRepository employeeRepository;

    @MockitoBean
    private TaskRepository taskRepository;

    @MockitoBean
    private FollowUpRepository followUpRepository;

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

    // ----- helpers -------------------------------------------------------

    private User enabledAdmin() {
        User user = new User();
        user.setId("admin-1");
        user.setEmail("admin@example.com");
        user.setPassword(new PasswordConfig()
                .passwordEncoder().encode(RAW_PASSWORD));
        user.setRole("ADMIN");
        user.setEnabled(true);
        return user;
    }

    private User enabledEmployee() {
        User user = new User();
        user.setId("employee-1");
        user.setEmail("employee@example.com");
        user.setPassword(new PasswordConfig()
                .passwordEncoder().encode(RAW_PASSWORD));
        user.setRole("EMPLOYEE");
        user.setEnabled(true);
        return user;
    }

    private SecretKey signingKey() {
        return Keys.hmacShaKeyFor(Decoders.BASE64.decode(TEST_JWT_SECRET));
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

    private Date now() {
        return new Date();
    }

    private String loginJson(String email, String password, String role) {
        StringBuilder json = new StringBuilder();
        json.append("{\"email\":\"").append(email).append("\",");
        json.append("\"password\":\"").append(password).append("\"");

        if (role != null) {
            json.append(",\"role\":\"").append(role).append("\"");
        }

        json.append("}");
        return json.toString();
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(
                    digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private String extractToken(String json) {
        String marker = "\"token\":\"";
        int start = json.indexOf(marker);
        org.junit.jupiter.api.Assertions.assertTrue(start >= 0,
                "token missing in response: " + json);
        start += marker.length();
        return json.substring(start, json.indexOf('"', start));
    }

    // ----- login: the two roles ----------------------------------------

    @Test
    void adminCanSignInThroughTheAdminOption() throws Exception {
        when(userRepository.findByEmailIgnoreCase("admin@example.com"))
                .thenReturn(Optional.of(enabledAdmin()));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson("admin@example.com",
                                RAW_PASSWORD, "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.role").value("ADMIN"))
                .andExpect(jsonPath("$.email").value("admin@example.com"))
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    void employeeCanSignInThroughTheEmployeeOption() throws Exception {
        when(userRepository.findByEmailIgnoreCase("employee@example.com"))
                .thenReturn(Optional.of(enabledEmployee()));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson("employee@example.com",
                                RAW_PASSWORD, "EMPLOYEE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.role").value("EMPLOYEE"))
                .andExpect(jsonPath("$.token").isNotEmpty());
    }

    @Test
    void legacyUserAccountsAreAcceptedAsEmployees() throws Exception {
        User legacy = enabledEmployee();
        legacy.setRole("USER");

        when(userRepository.findByEmailIgnoreCase("employee@example.com"))
                .thenReturn(Optional.of(legacy));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson("employee@example.com",
                                RAW_PASSWORD, "EMPLOYEE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("EMPLOYEE"));
    }

    @Test
    void loginWithoutRoleStillWorks() throws Exception {
        when(userRepository.findByEmailIgnoreCase("employee@example.com"))
                .thenReturn(Optional.of(enabledEmployee()));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson("employee@example.com",
                                RAW_PASSWORD, null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("EMPLOYEE"));
    }

    @Test
    void employeeCannotSignInThroughTheAdminOption() throws Exception {
        when(userRepository.findByEmailIgnoreCase("employee@example.com"))
                .thenReturn(Optional.of(enabledEmployee()));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson("employee@example.com",
                                RAW_PASSWORD, "ADMIN")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value("ERROR"));
    }

    @Test
    void adminCannotSignInThroughTheEmployeeOption() throws Exception {
        when(userRepository.findByEmailIgnoreCase("admin@example.com"))
                .thenReturn(Optional.of(enabledAdmin()));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson("admin@example.com",
                                RAW_PASSWORD, "EMPLOYEE")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value("ERROR"));
    }

    @Test
    void unknownRoleInThePayloadIsRejected() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson("admin@example.com",
                                RAW_PASSWORD, "SUPERADMIN")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("ERROR"));
    }

    @Test
    void invalidPasswordIsRejected() throws Exception {
        when(userRepository.findByEmailIgnoreCase("employee@example.com"))
                .thenReturn(Optional.of(enabledEmployee()));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson("employee@example.com",
                                "wrong-password", "EMPLOYEE")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message")
                        .value("Invalid email or password"));
    }

    @Test
    void disabledAccountIsRejected() throws Exception {
        User disabled = enabledEmployee();
        disabled.setEnabled(false);

        when(userRepository.findByEmailIgnoreCase("employee@example.com"))
                .thenReturn(Optional.of(disabled));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson("employee@example.com",
                                RAW_PASSWORD, "EMPLOYEE")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void blankCredentialsAreRejectedByValidation() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"\",\"password\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("ERROR"));
    }

    // ----- role based authorization -------------------------------------

    @Test
    void adminTokenCannotReachEmployeeOnlyEndpoints() throws Exception {
        when(userRepository.findByEmailIgnoreCase("admin@example.com"))
                .thenReturn(Optional.of(enabledAdmin()));

        mockMvc.perform(get("/api/employee/overview")
                        .header("Authorization",
                                "Bearer " + validTokenFor("admin@example.com")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Forbidden"));
    }

    @Test
    void employeeTokenCannotReachAdminOnlyEndpoints() throws Exception {
        when(userRepository.findByEmailIgnoreCase("employee@example.com"))
                .thenReturn(Optional.of(enabledEmployee()));

        mockMvc.perform(get("/api/admin/overview")
                        .header("Authorization",
                                "Bearer " + validTokenFor("employee@example.com")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Forbidden"));
    }

    @Test
    void adminTokenReachesTheAdminOverview() throws Exception {
        when(userRepository.findByEmailIgnoreCase("admin@example.com"))
                .thenReturn(Optional.of(enabledAdmin()));
        when(metricsService.metrics()).thenReturn(Map.of("totalProjects", 3));

        mockMvc.perform(get("/api/admin/overview")
                        .header("Authorization",
                                "Bearer " + validTokenFor("admin@example.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ADMIN"))
                .andExpect(jsonPath("$.metrics.totalProjects").value(3));
    }

    @Test
    void employeeTokenReachesTheEmployeeOverview() throws Exception {
        when(userRepository.findByEmailIgnoreCase("employee@example.com"))
                .thenReturn(Optional.of(enabledEmployee()));
        when(employeeRepository.findByWorkEmailIgnoreCase("employee@example.com"))
                .thenReturn(Optional.empty());

        mockMvc.perform(get("/api/employee/overview")
                        .header("Authorization",
                                "Bearer " + validTokenFor("employee@example.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("EMPLOYEE"))
                .andExpect(jsonPath("$.metrics.assignedTasks").value(0));
    }

    @Test
    void missingTokenIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/admin/overview"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Unauthorized"));
    }

    @Test
    void invalidTokenIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/admin/overview")
                        .header("Authorization", "Bearer not.a-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message")
                        .value("Invalid or expired token"));
    }

    @Test
    void meEndpointNeedsAToken() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void meEndpointReturnsTheIdentityAndRole() throws Exception {
        when(userRepository.findByEmailIgnoreCase("employee@example.com"))
                .thenReturn(Optional.of(enabledEmployee()));

        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization",
                                "Bearer " + validTokenFor("employee@example.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("employee@example.com"))
                .andExpect(jsonPath("$.role").value("EMPLOYEE"))
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    // ----- forgot password ----------------------------------------------

    @Test
    void forgotPasswordAnswersGenericallyForAnExistingEmail()
            throws Exception {
        when(userRepository.findByEmailIgnoreCase("employee@example.com"))
                .thenReturn(Optional.of(enabledEmployee()));
        when(tokenRepository.save(any(PasswordResetToken.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        mockMvc.perform(post("/api/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"employee@example.com\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.message").value(
                        AuthController.GENERIC_RESET_MESSAGE))
                .andExpect(jsonPath("$.token").doesNotExist())
                .andExpect(jsonPath("$.resetToken").doesNotExist());
    }

    @Test
    void forgotPasswordAnswersGenericallyForAnUnknownEmail()
            throws Exception {
        when(userRepository.findByEmailIgnoreCase("ghost@example.com"))
                .thenReturn(Optional.empty());

        mockMvc.perform(post("/api/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"ghost@example.com\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.message").value(
                        AuthController.GENERIC_RESET_MESSAGE));
    }

    @Test
    void forgotPasswordGivesTheSameAnswerForBothCases() throws Exception {
        when(userRepository.findByEmailIgnoreCase("employee@example.com"))
                .thenReturn(Optional.of(enabledEmployee()));
        when(tokenRepository.save(any(PasswordResetToken.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        String existing = mockMvc.perform(
                        post("/api/auth/forgot-password")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"email\":\"employee@example.com\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        String unknown = mockMvc.perform(
                        post("/api/auth/forgot-password")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"email\":\"ghost@example.com\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertEquals(existing, unknown);
    }

    @Test
    void forgotPasswordNeverStoresTheRawToken() throws Exception {
        when(userRepository.findByEmailIgnoreCase("employee@example.com"))
                .thenReturn(Optional.of(enabledEmployee()));
        when(tokenRepository.save(any(PasswordResetToken.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        mockMvc.perform(post("/api/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"employee@example.com\"}"))
                .andExpect(status().isOk());

        ArgumentCaptor<PasswordResetToken> captor =
                ArgumentCaptor.forClass(PasswordResetToken.class);
        verify(tokenRepository).save(captor.capture());

        PasswordResetToken stored = captor.getValue();
        assertNotNull(stored.getTokenHash());
        assertEquals(64, stored.getTokenHash().length());
        assertTrue(stored.getExpiresAt().isAfter(Instant.now()));
    }

    @Test
    void forgotPasswordRejectsAMalformedEmail() throws Exception {
        mockMvc.perform(post("/api/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"not-an-email\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("ERROR"));
    }

    // ----- reset password ------------------------------------------------

    private PasswordResetToken resetTokenFor(String userId) {
        PasswordResetToken token = new PasswordResetToken();
        token.setId("token-1");
        token.setTokenHash(sha256("reset-token-123"));
        token.setUserId(userId);
        token.setUserEmail("employee@example.com");
        token.setCreatedAt(Instant.now());
        token.setExpiresAt(Instant.now().plusSeconds(600));
        return token;
    }

    @Test
    void resetPasswordUpdatesTheAccount() throws Exception {
        User employee = enabledEmployee();
        PasswordResetToken token = resetTokenFor("employee-1");

        when(tokenRepository.findByTokenHash(token.getTokenHash()))
                .thenReturn(Optional.of(token));
        when(userRepository.findById("employee-1"))
                .thenReturn(Optional.of(employee));
        when(userRepository.save(any(User.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(tokenRepository.save(any(PasswordResetToken.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        mockMvc.perform(post("/api/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"reset-token-123\","
                                + "\"password\":\"" + NEW_PASSWORD + "\","
                                + "\"confirmPassword\":\"" + NEW_PASSWORD
                                + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.token").doesNotExist());

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());

        User saved = captor.getValue();
        assertNotEquals(NEW_PASSWORD, saved.getPassword());
        assertTrue(new PasswordConfig().passwordEncoder()
                .matches(NEW_PASSWORD, saved.getPassword()));
        assertFalse(new PasswordConfig().passwordEncoder()
                .matches(RAW_PASSWORD, saved.getPassword()));

        verify(tokenRepository).deleteAllByUserId("employee-1");
    }

    @Test
    void resetPasswordInvalidatesTheTokenForReuse() throws Exception {
        User employee = enabledEmployee();
        PasswordResetToken token = resetTokenFor("employee-1");

        when(tokenRepository.findByTokenHash(token.getTokenHash()))
                .thenReturn(Optional.of(token))
                .thenReturn(Optional.empty());
        when(userRepository.findById("employee-1"))
                .thenReturn(Optional.of(employee));
        when(userRepository.save(any(User.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(tokenRepository.save(any(PasswordResetToken.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        String body = "{\"token\":\"reset-token-123\","
                + "\"password\":\"" + NEW_PASSWORD + "\","
                + "\"confirmPassword\":\"" + NEW_PASSWORD + "\"}";

        mockMvc.perform(post("/api/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("ERROR"));
    }

    @Test
    void resetPasswordRejectsAnUnknownToken() throws Exception {
        when(tokenRepository.findByTokenHash(sha256("nope")))
                .thenReturn(Optional.empty());

        mockMvc.perform(post("/api/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"nope\","
                                + "\"password\":\"" + NEW_PASSWORD + "\","
                                + "\"confirmPassword\":\"" + NEW_PASSWORD
                                + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("ERROR"));

        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void resetPasswordRejectsAnExpiredToken() throws Exception {
        PasswordResetToken token = resetTokenFor("employee-1");
        token.setExpiresAt(Instant.now().minusSeconds(60));

        when(tokenRepository.findByTokenHash(token.getTokenHash()))
                .thenReturn(Optional.of(token));

        mockMvc.perform(post("/api/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"reset-token-123\","
                                + "\"password\":\"" + NEW_PASSWORD + "\","
                                + "\"confirmPassword\":\"" + NEW_PASSWORD
                                + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("ERROR"));

        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void resetPasswordRejectsAnAlreadyUsedToken() throws Exception {
        PasswordResetToken token = resetTokenFor("employee-1");
        token.setUsedAt(Instant.now().minusSeconds(30));

        when(tokenRepository.findByTokenHash(token.getTokenHash()))
                .thenReturn(Optional.of(token));

        mockMvc.perform(post("/api/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"reset-token-123\","
                                + "\"password\":\"" + NEW_PASSWORD + "\","
                                + "\"confirmPassword\":\"" + NEW_PASSWORD
                                + "\"}"))
                .andExpect(status().isBadRequest());

        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void resetPasswordRejectsAWeakPassword() throws Exception {
        PasswordResetToken token = resetTokenFor("employee-1");

        when(tokenRepository.findByTokenHash(token.getTokenHash()))
                .thenReturn(Optional.of(token));

        mockMvc.perform(post("/api/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"reset-token-123\","
                                + "\"password\":\"weak\","
                                + "\"confirmPassword\":\"weak\"}"))
                .andExpect(status().isBadRequest());

        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void resetPasswordRejectsAMismatchedConfirmation() throws Exception {
        PasswordResetToken token = resetTokenFor("employee-1");

        when(tokenRepository.findByTokenHash(token.getTokenHash()))
                .thenReturn(Optional.of(token));

        mockMvc.perform(post("/api/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"reset-token-123\","
                                + "\"password\":\"" + NEW_PASSWORD + "\","
                                + "\"confirmPassword\":\"SomethingElse-123!\"}"))
                .andExpect(status().isBadRequest());

        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void loginWorksWithTheNewPassword() throws Exception {
        User employee = enabledEmployee();
        PasswordResetToken token = resetTokenFor("employee-1");

        when(tokenRepository.findByTokenHash(token.getTokenHash()))
                .thenReturn(Optional.of(token));
        when(userRepository.findById("employee-1"))
                .thenReturn(Optional.of(employee));
        when(userRepository.save(any(User.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(tokenRepository.save(any(PasswordResetToken.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        mockMvc.perform(post("/api/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"reset-token-123\","
                                + "\"password\":\"" + NEW_PASSWORD + "\","
                                + "\"confirmPassword\":\"" + NEW_PASSWORD
                                + "\"}"))
                .andExpect(status().isOk());

        when(userRepository.findByEmailIgnoreCase("employee@example.com"))
                .thenReturn(Optional.of(employee));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson("employee@example.com",
                                NEW_PASSWORD, "EMPLOYEE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("EMPLOYEE"))
                .andExpect(jsonPath("$.token").isNotEmpty());
    }

    @Test
    void resetDoesNotSignTheUserIn() throws Exception {
        PasswordResetToken token = resetTokenFor("employee-1");
        User employee = enabledEmployee();

        when(tokenRepository.findByTokenHash(token.getTokenHash()))
                .thenReturn(Optional.of(token));
        when(userRepository.findById("employee-1"))
                .thenReturn(Optional.of(employee));
        when(userRepository.save(any(User.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(tokenRepository.save(any(PasswordResetToken.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        String body = mockMvc.perform(post("/api/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"reset-token-123\","
                                + "\"password\":\"" + NEW_PASSWORD + "\","
                                + "\"confirmPassword\":\"" + NEW_PASSWORD
                                + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertFalse(body.contains("\"token\""));
        assertFalse(body.contains(NEW_PASSWORD));
    }

    @Test
    void loginResponseNeverContainsThePasswordHash() throws Exception {
        when(userRepository.findByEmailIgnoreCase("employee@example.com"))
                .thenReturn(Optional.of(enabledEmployee()));

        String body = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson("employee@example.com",
                                RAW_PASSWORD, "EMPLOYEE")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertFalse(body.contains("$2a$"));
        assertFalse(body.contains(RAW_PASSWORD));
        assertNotNull(extractToken(body));
    }
}
