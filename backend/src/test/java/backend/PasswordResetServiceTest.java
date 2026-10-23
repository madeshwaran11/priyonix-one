
package backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Unit tests for the password reset token lifecycle.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PasswordResetServiceTest {

    private static final String RAW_PASSWORD = "Sup3rSecret-Passw0rd!";
    private static final String NEW_PASSWORD = "N3wStrong-Passw0rd!";
    private static final String USER_ID = "user-1";

    @Mock
    private PasswordResetTokenRepository tokenRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private SecureTokenGenerator tokenGenerator;

    @Mock
    private RefreshTokenService refreshTokenService;

    @Mock
    private AuthProperties authProperties;

    private final PasswordEncoder passwordEncoder =
            new BCryptPasswordEncoder();

    private PasswordResetService passwordResetService;

    @BeforeEach
    void setUp() {
        when(authProperties.getResetTokenTtl())
                .thenReturn(Duration.ofMinutes(15));

        passwordResetService = new PasswordResetService(
                tokenRepository,
                userRepository,
                passwordEncoder,
                tokenGenerator,
                refreshTokenService,
                authProperties);
    }

    private User enabledUser() {
        User user = new User();
        user.setId(USER_ID);
        user.setEmail("employee@example.com");
        user.setPassword(passwordEncoder.encode(RAW_PASSWORD));
        user.setRole("EMPLOYEE");
        user.setEnabled(true);
        return user;
    }

    private void stubGenerator(String rawToken) {
        when(tokenGenerator.generate()).thenReturn(rawToken);
        when(tokenGenerator.hash(rawToken))
                .thenReturn("hash-of-" + rawToken);
    }

    @Test
    void issuedTokenStoresOnlyTheHash() {
        stubGenerator("raw-token-value");

        PasswordResetService.IssuedToken issued = passwordResetService
                .issue(enabledUser());

        assertEquals("raw-token-value", issued.rawToken());
        assertTrue(issued.expiresAt().isAfter(Instant.now()));

        org.mockito.ArgumentCaptor<PasswordResetToken> captor =
                org.mockito.ArgumentCaptor.forClass(PasswordResetToken.class);
        verify(tokenRepository).save(captor.capture());

        PasswordResetToken stored = captor.getValue();
        assertEquals("hash-of-raw-token-value", stored.getTokenHash());
        assertNotEquals(issued.rawToken(), stored.getTokenHash());
        assertEquals(USER_ID, stored.getUserId());
    }

    @Test
    void issuingANewTokenRemovesThePreviousOnes() {
        stubGenerator("raw-token-value");

        passwordResetService.issue(enabledUser());

        verify(tokenRepository).deleteAllByUserId(USER_ID);
    }

    @Test
    void validTokenIsAccepted() {
        PasswordResetToken token = new PasswordResetToken();
        token.setId("token-1");
        token.setTokenHash("hash-of-raw");
        token.setUserId(USER_ID);
        token.setExpiresAt(Instant.now().plusSeconds(600));

        when(tokenGenerator.hash("raw")).thenReturn("hash-of-raw");
        when(tokenRepository.findByTokenHash("hash-of-raw"))
                .thenReturn(Optional.of(token));

        assertTrue(passwordResetService.findValidToken("raw").isPresent());
    }

    @Test
    void expiredTokenIsRejected() {
        PasswordResetToken token = new PasswordResetToken();
        token.setTokenHash("hash-of-raw");
        token.setUserId(USER_ID);
        token.setExpiresAt(Instant.now().minusSeconds(60));

        when(tokenGenerator.hash("raw")).thenReturn("hash-of-raw");
        when(tokenRepository.findByTokenHash("hash-of-raw"))
                .thenReturn(Optional.of(token));

        assertTrue(passwordResetService.findValidToken("raw").isEmpty());
    }

    @Test
    void usedTokenIsRejected() {
        PasswordResetToken token = new PasswordResetToken();
        token.setTokenHash("hash-of-raw");
        token.setUserId(USER_ID);
        token.setExpiresAt(Instant.now().plusSeconds(600));
        token.setUsedAt(Instant.now().minusSeconds(30));

        when(tokenGenerator.hash("raw")).thenReturn("hash-of-raw");
        when(tokenRepository.findByTokenHash("hash-of-raw"))
                .thenReturn(Optional.of(token));

        assertTrue(passwordResetService.findValidToken("raw").isEmpty());
    }

    @Test
    void unknownTokenIsRejected() {
        when(tokenGenerator.hash("unknown")).thenReturn("hash-of-unknown");
        when(tokenRepository.findByTokenHash("hash-of-unknown"))
                .thenReturn(Optional.empty());

        assertTrue(passwordResetService.findValidToken("unknown").isEmpty());
    }

    @Test
    void resetStoresTheNewPasswordAsBcryptHash() {
        User user = enabledUser();

        PasswordResetToken token = new PasswordResetToken();
        token.setId("token-1");
        token.setTokenHash("hash-of-raw");
        token.setUserId(USER_ID);
        token.setExpiresAt(Instant.now().plusSeconds(600));

        when(tokenGenerator.hash("raw")).thenReturn("hash-of-raw");
        when(tokenRepository.findByTokenHash("hash-of-raw"))
                .thenReturn(Optional.of(token));
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        passwordResetService.resetPassword("raw", NEW_PASSWORD, NEW_PASSWORD);

        assertNotEquals(NEW_PASSWORD, user.getPassword());
        assertTrue(passwordEncoder.matches(NEW_PASSWORD, user.getPassword()));
        assertFalse(passwordEncoder.matches(RAW_PASSWORD, user.getPassword()));

        verify(tokenRepository).deleteAllByUserId(USER_ID);
    }

    @Test
    void resetEndsEveryRememberedSession() {
        User user = enabledUser();

        PasswordResetToken token = new PasswordResetToken();
        token.setId("token-1");
        token.setTokenHash("hash-of-raw");
        token.setUserId(USER_ID);
        token.setExpiresAt(Instant.now().plusSeconds(600));

        when(tokenGenerator.hash("raw")).thenReturn("hash-of-raw");
        when(tokenRepository.findByTokenHash("hash-of-raw"))
                .thenReturn(Optional.of(token));
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));

        passwordResetService.resetPassword("raw", NEW_PASSWORD, NEW_PASSWORD);

        verify(refreshTokenService).revokeAllForUser(USER_ID);
    }

    @Test
    void consumedTokenCannotBeUsedTwice() {
        PasswordResetToken token = new PasswordResetToken();
        token.setId("token-1");
        token.setTokenHash("hash-of-raw");
        token.setUserId(USER_ID);
        token.setExpiresAt(Instant.now().plusSeconds(600));

        when(tokenGenerator.hash("raw")).thenReturn("hash-of-raw");
        when(tokenRepository.findByTokenHash("hash-of-raw"))
                .thenReturn(Optional.of(token));

        token.setUsedAt(Instant.now());

        assertThrows(InvalidResetTokenException.class, () ->
                passwordResetService.resetPassword(
                        "raw", NEW_PASSWORD, NEW_PASSWORD));
    }

    @Test
    void weakPasswordIsRejected() {
        assertThrows(InvalidRequestException.class, () ->
                passwordResetService.resetPassword(
                        "raw", "weak", "weak"));

        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void mismatchedConfirmationIsRejected() {
        assertThrows(InvalidRequestException.class, () ->
                passwordResetService.resetPassword(
                        "raw", NEW_PASSWORD, "different-password"));
    }

    @Test
    void blankTokenIsRejected() {
        assertTrue(passwordResetService.findValidToken("  ").isEmpty());
        assertTrue(passwordResetService.findValidToken(null).isEmpty());
        verify(tokenRepository, never())
                .findByTokenHash(anyString());
    }
}
