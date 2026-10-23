
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
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
import org.springframework.test.web.servlet.MvcResult;

/**
 * End-to-end tests for the "Remember me" option.
 *
 * The suite runs against the real Spring Security filter chain and the real
 * controllers and services. Only the MongoDB layer is mocked, so the tests
 * never touch the Atlas cluster.
 *
 * A remembered session is backed by an HttpOnly refresh cookie. The cookie
 * is the only place where the refresh token exists in clear text: the
 * database stores nothing but its SHA-256 hash.
 */
@SpringBootTest(properties = {
        "spring.mongodb.uri=mongodb://127.0.0.1:27017/priyonix-test",
        "JWT_SECRET=dGVzdC1zZWNyZXQtZm9yLXVuaXQtYnVpbGRzLW9ubHktMzItYnl0ZXM=",
        "ADMIN_EMAIL=",
        "ADMIN_PASSWORD=",
        "AUTH_LOGIN_MAX_IP_ATTEMPTS=1000",
        "AUTH_REFRESH_COOKIE_NAME=px_refresh",
        "AUTH_REFRESH_COOKIE_SECURE=false",
        "AUTH_REFRESH_COOKIE_SAME_SITE=Lax"
})
@AutoConfigureMockMvc
class AuthRememberMeIntegrationTests extends PlatformMockDatabaseTestSupport {

    private static final String RAW_PASSWORD = "Sup3rSecret-Passw0rd!";

    private static final String COOKIE_NAME = "px_refresh";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private RefreshTokenRepository refreshTokenRepository;

    @MockitoBean
    private PasswordResetTokenRepository resetTokenRepository;

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

    // ----- helpers -------------------------------------------------------

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

    private String loginJson(String email, String password, boolean rememberMe) {
        return "{\"email\":\"" + email + "\",\"password\":\"" + password
                + "\",\"rememberMe\":" + rememberMe + "}";
    }

    private String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(
                    digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    /** All Set-Cookie headers of a response. */
    private List<String> setCookieHeaders(MvcResult result) {
        return result.getResponse().getHeaders("Set-Cookie");
    }

    /** The raw value of the refresh cookie, or null when it is absent. */
    private String refreshCookieValue(MvcResult result) {
        for (String header : setCookieHeaders(result)) {
            if (!header.startsWith(COOKIE_NAME + "=")) {
                continue;
            }

            String value = header.substring(COOKIE_NAME.length() + 1);
            int separator = value.indexOf(';');

            return separator >= 0 ? value.substring(0, separator) : value;
        }

        return null;
    }

    /** The full Set-Cookie header of the refresh cookie. */
    private String refreshCookieHeader(MvcResult result) {
        for (String header : setCookieHeaders(result)) {
            if (header.startsWith(COOKIE_NAME + "=")) {
                return header;
            }
        }

        return "";
    }

    private RefreshToken storedToken(String rawValue) {
        RefreshToken stored = new RefreshToken();
        stored.setId("refresh-1");
        stored.setTokenHash(sha256Hex(rawValue));
        stored.setUserId("employee-1");
        stored.setUserEmail("employee@example.com");
        stored.setCreatedAt(Instant.now());
        stored.setExpiresAt(Instant.now().plusSeconds(3600));
        return stored;
    }

    // ----- login ---------------------------------------------------------

    @Test
    void aPlainSignInStoresNoRefreshToken() throws Exception {
        when(userRepository.findByEmailIgnoreCase("employee@example.com"))
                .thenReturn(Optional.of(enabledEmployee()));

        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson(
                                "employee@example.com", RAW_PASSWORD, false)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rememberMe").value(false))
                .andReturn();

        // Any older refresh cookie is cleared, but nothing is issued.
        String value = refreshCookieValue(result);
        assertNotNull(value);
        assertEquals("", value,
                "a plain sign-in must not hand out a refresh token");
        assertTrue(refreshCookieHeader(result).contains("Max-Age=0"),
                "the refresh cookie must be cleared: "
                        + refreshCookieHeader(result));

        verify(refreshTokenRepository, never()).save(any(RefreshToken.class));
    }

    @Test
    void aRememberedSignInIssuesAnHttpOnlyRefreshCookie() throws Exception {
        when(userRepository.findByEmailIgnoreCase("employee@example.com"))
                .thenReturn(Optional.of(enabledEmployee()));

        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson(
                                "employee@example.com", RAW_PASSWORD, true)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rememberMe").value(true))
                .andReturn();

        String value = refreshCookieValue(result);

        assertNotNull(value, "the refresh cookie is missing");
        assertFalse(value.isBlank(), "the refresh cookie must carry a value");

        String header = refreshCookieHeader(result);

        assertTrue(header.contains("HttpOnly"),
                "the refresh cookie must be HttpOnly: " + header);
        assertTrue(header.contains("Path=/api/auth"),
                "the refresh cookie must be limited to the auth endpoints: "
                        + header);
        assertTrue(header.contains("SameSite=Lax"),
                "the refresh cookie must declare a SameSite policy: " + header);
    }

    @Test
    void theRefreshTokenIsNeverReturnedInTheBody() throws Exception {
        when(userRepository.findByEmailIgnoreCase("employee@example.com"))
                .thenReturn(Optional.of(enabledEmployee()));

        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson(
                                "employee@example.com", RAW_PASSWORD, true)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.refreshToken").doesNotExist())
                .andReturn();

        String value = refreshCookieValue(result);
        String body = result.getResponse().getContentAsString();

        assertNotNull(value);
        assertFalse(body.contains(value),
                "the refresh token must not appear in the response body");
    }

    @Test
    void onlyTheHashOfTheRefreshTokenIsStored() throws Exception {
        when(userRepository.findByEmailIgnoreCase("employee@example.com"))
                .thenReturn(Optional.of(enabledEmployee()));

        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson(
                                "employee@example.com", RAW_PASSWORD, true)))
                .andExpect(status().isOk())
                .andReturn();

        String value = refreshCookieValue(result);
        assertNotNull(value);

        org.mockito.ArgumentCaptor<RefreshToken> captor =
                org.mockito.ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(captor.capture());

        RefreshToken stored = captor.getValue();

        assertNotEquals(value, stored.getTokenHash(),
                "the raw refresh token must never be stored");
        assertEquals(sha256Hex(value), stored.getTokenHash());
        assertEquals("employee-1", stored.getUserId());
        assertTrue(stored.getExpiresAt().isAfter(Instant.now()));
    }

    // ----- refresh -------------------------------------------------------

    @Test
    void theRefreshCookieExchangesForANewAccessToken() throws Exception {
        when(userRepository.findByEmailIgnoreCase("employee@example.com"))
                .thenReturn(Optional.of(enabledEmployee()));
        when(userRepository.findById("employee-1"))
                .thenReturn(Optional.of(enabledEmployee()));

        String rawValue = "the-refresh-token";

        when(refreshTokenRepository.findByTokenHash(sha256Hex(rawValue)))
                .thenReturn(Optional.of(storedToken(rawValue)));

        MvcResult refreshed = mockMvc.perform(post("/api/auth/refresh")
                        .cookie(new jakarta.servlet.http.Cookie(
                                COOKIE_NAME, rawValue)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.email").value("employee@example.com"))
                .andExpect(jsonPath("$.role").value("EMPLOYEE"))
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andReturn();

        // The successor token is handed back in a fresh cookie.
        String successor = refreshCookieValue(refreshed);
        assertNotNull(successor);
        assertFalse(successor.isBlank());
        assertNotEquals(rawValue, successor,
                "the refresh token must be rotated");
    }

    @Test
    void theUsedRefreshTokenIsRevokedByTheRotation() throws Exception {
        when(userRepository.findById("employee-1"))
                .thenReturn(Optional.of(enabledEmployee()));

        String rawValue = "the-refresh-token";
        RefreshToken stored = storedToken(rawValue);

        when(refreshTokenRepository.findByTokenHash(sha256Hex(rawValue)))
                .thenReturn(Optional.of(stored));

        mockMvc.perform(post("/api/auth/refresh")
                        .cookie(new jakarta.servlet.http.Cookie(
                                COOKIE_NAME, rawValue)))
                .andExpect(status().isOk());

        assertNotNull(stored.getRevokedAt(),
                "the used refresh token must be revoked");
        assertNotNull(stored.getRotatedAt(),
                "the used refresh token must be marked as rotated");
    }

    @Test
    void aRefreshWithoutACookieIsRejected() throws Exception {
        mockMvc.perform(post("/api/auth/refresh"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value("ERROR"));
    }

    @Test
    void anUnknownRefreshTokenIsRejected() throws Exception {
        String rawValue = "unknown-refresh-token";

        when(refreshTokenRepository.findByTokenHash(sha256Hex(rawValue)))
                .thenReturn(Optional.empty());

        mockMvc.perform(post("/api/auth/refresh")
                        .cookie(new jakarta.servlet.http.Cookie(
                                COOKIE_NAME, rawValue)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void anExpiredRefreshTokenIsRejected() throws Exception {
        String rawValue = "expired-refresh-token";

        RefreshToken stored = storedToken(rawValue);
        stored.setCreatedAt(Instant.now().minusSeconds(7200));
        stored.setExpiresAt(Instant.now().minusSeconds(3600));

        when(refreshTokenRepository.findByTokenHash(sha256Hex(rawValue)))
                .thenReturn(Optional.of(stored));

        mockMvc.perform(post("/api/auth/refresh")
                        .cookie(new jakarta.servlet.http.Cookie(
                                COOKIE_NAME, rawValue)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void aRevokedRefreshTokenIsRejected() throws Exception {
        String rawValue = "revoked-refresh-token";

        RefreshToken stored = storedToken(rawValue);
        stored.setRevokedAt(Instant.now());

        when(refreshTokenRepository.findByTokenHash(sha256Hex(rawValue)))
                .thenReturn(Optional.of(stored));

        mockMvc.perform(post("/api/auth/refresh")
                        .cookie(new jakarta.servlet.http.Cookie(
                                COOKIE_NAME, rawValue)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void aRefreshForADisabledAccountIsRejected() throws Exception {
        String rawValue = "disabled-refresh-token";

        User disabled = enabledEmployee();
        disabled.setEnabled(false);

        when(refreshTokenRepository.findByTokenHash(sha256Hex(rawValue)))
                .thenReturn(Optional.of(storedToken(rawValue)));
        when(userRepository.findById("employee-1"))
                .thenReturn(Optional.of(disabled));

        mockMvc.perform(post("/api/auth/refresh")
                        .cookie(new jakarta.servlet.http.Cookie(
                                COOKIE_NAME, rawValue)))
                .andExpect(status().isUnauthorized());
    }

    // ----- sign out ------------------------------------------------------

    @Test
    void signingOutRevokesTheRefreshTokenAndClearsTheCookie() throws Exception {
        String rawValue = "logout-refresh-token";
        RefreshToken stored = storedToken(rawValue);

        when(refreshTokenRepository.findByTokenHash(sha256Hex(rawValue)))
                .thenReturn(Optional.of(stored));

        MvcResult result = mockMvc.perform(post("/api/auth/logout")
                        .cookie(new jakarta.servlet.http.Cookie(
                                COOKIE_NAME, rawValue)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andReturn();

        verify(refreshTokenRepository).save(stored);
        assertNotNull(stored.getRevokedAt(),
                "the refresh token must be revoked on sign out");

        String value = refreshCookieValue(result);
        assertNotNull(value);
        assertEquals("", value,
                "the cookie must be cleared in the browser");
        assertTrue(refreshCookieHeader(result).contains("Max-Age=0"),
                "the cookie must expire immediately: "
                        + refreshCookieHeader(result));
    }

    @Test
    void signingOutWithoutACookieStillSucceeds() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/logout"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andReturn();

        // No token is revoked, but the cookie is cleared anyway so the
        // browser never keeps a stale one.
        verify(refreshTokenRepository, never()).save(any(RefreshToken.class));

        String value = refreshCookieValue(result);
        assertNotNull(value);
        assertEquals("", value);
    }
}
