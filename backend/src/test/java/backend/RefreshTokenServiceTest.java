
package backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * Unit tests for the refresh token lifecycle that backs the "Remember me"
 * option.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RefreshTokenServiceTest {

    private static final String USER_ID = "user-1";

    @Mock
    private RefreshTokenRepository tokenRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private SecureTokenGenerator tokenGenerator;

    @Mock
    private AuthProperties authProperties;

    private RefreshTokenService refreshTokenService;

    @BeforeEach
    void setUp() {
        when(authProperties.getRefreshTokenTtl())
                .thenReturn(Duration.ofDays(30));

        refreshTokenService = new RefreshTokenService(
                tokenRepository,
                userRepository,
                tokenGenerator,
                authProperties);
    }

    private User enabledUser() {
        User user = new User();
        user.setId(USER_ID);
        user.setEmail("employee@example.com");
        user.setRole("EMPLOYEE");
        user.setEnabled(true);
        return user;
    }

    private void stubGenerator(String rawToken) {
        when(tokenGenerator.generate()).thenReturn(rawToken);
        when(tokenGenerator.hash(rawToken)).thenReturn("hash-of-" + rawToken);
    }

    @Test
    void issuedTokenStoresOnlyTheHash() {
        stubGenerator("raw-token-value");

        RefreshTokenService.IssuedToken issued =
                refreshTokenService.issue(enabledUser());

        assertEquals("raw-token-value", issued.rawToken());
        assertTrue(issued.expiresAt().isAfter(Instant.now()));

        org.mockito.ArgumentCaptor<RefreshToken> captor =
                org.mockito.ArgumentCaptor.forClass(RefreshToken.class);
        verify(tokenRepository).save(captor.capture());

        RefreshToken stored = captor.getValue();

        assertEquals("hash-of-raw-token-value", stored.getTokenHash());
        assertNotEquals(issued.rawToken(), stored.getTokenHash());
        assertEquals(USER_ID, stored.getUserId());
        assertEquals("employee@example.com", stored.getUserEmail());
    }

    @Test
    void issuingANewTokenRevokesThePreviousOnes() {
        stubGenerator("raw-token-value");

        RefreshToken previous = new RefreshToken();
        previous.setUserId(USER_ID);
        previous.setTokenHash("hash-of-old");

        when(tokenRepository.findAllByUserId(USER_ID))
                .thenReturn(List.of(previous));

        refreshTokenService.issue(enabledUser());

        assertNotEquals(null, previous.getRevokedAt(),
                "the previous token must be revoked");
    }

    @Test
    void validTokenIsAccepted() {
        RefreshToken token = new RefreshToken();
        token.setId("token-1");
        token.setTokenHash("hash-of-raw");
        token.setUserId(USER_ID);
        token.setExpiresAt(Instant.now().plusSeconds(600));

        when(tokenGenerator.hash("raw")).thenReturn("hash-of-raw");
        when(tokenRepository.findByTokenHash("hash-of-raw"))
                .thenReturn(Optional.of(token));
        when(userRepository.findById(USER_ID))
                .thenReturn(Optional.of(enabledUser()));

        assertTrue(refreshTokenService.findValidToken("raw").isPresent());
    }

    @Test
    void expiredTokenIsRejected() {
        RefreshToken token = new RefreshToken();
        token.setTokenHash("hash-of-raw");
        token.setUserId(USER_ID);
        token.setExpiresAt(Instant.now().minusSeconds(60));

        when(tokenGenerator.hash("raw")).thenReturn("hash-of-raw");
        when(tokenRepository.findByTokenHash("hash-of-raw"))
                .thenReturn(Optional.of(token));

        assertTrue(refreshTokenService.findValidToken("raw").isEmpty());
    }

    @Test
    void revokedTokenIsRejected() {
        RefreshToken token = new RefreshToken();
        token.setTokenHash("hash-of-raw");
        token.setUserId(USER_ID);
        token.setExpiresAt(Instant.now().plusSeconds(600));
        token.setRevokedAt(Instant.now());

        when(tokenGenerator.hash("raw")).thenReturn("hash-of-raw");
        when(tokenRepository.findByTokenHash("hash-of-raw"))
                .thenReturn(Optional.of(token));

        assertTrue(refreshTokenService.findValidToken("raw").isEmpty());
    }

    @Test
    void tokenOfADisabledAccountIsRejected() {
        RefreshToken token = new RefreshToken();
        token.setTokenHash("hash-of-raw");
        token.setUserId(USER_ID);
        token.setExpiresAt(Instant.now().plusSeconds(600));

        User disabled = enabledUser();
        disabled.setEnabled(false);

        when(tokenGenerator.hash("raw")).thenReturn("hash-of-raw");
        when(tokenRepository.findByTokenHash("hash-of-raw"))
                .thenReturn(Optional.of(token));
        when(userRepository.findById(USER_ID))
                .thenReturn(Optional.of(disabled));

        assertTrue(refreshTokenService.findValidToken("raw").isEmpty());
    }

    @Test
    void blankTokenIsRejected() {
        assertTrue(refreshTokenService.findValidToken("  ").isEmpty());
        assertTrue(refreshTokenService.findValidToken(null).isEmpty());
        verify(tokenRepository, never())
                .findByTokenHash(any());
    }

    @Test
    void rotationRevokesTheOldTokenAndIssuesASuccessor() {
        stubGenerator("new-raw-token");

        RefreshToken token = new RefreshToken();
        token.setId("token-1");
        token.setTokenHash("hash-of-raw");
        token.setUserId(USER_ID);
        token.setExpiresAt(Instant.now().plusSeconds(600));

        when(tokenGenerator.hash("raw")).thenReturn("hash-of-raw");
        when(tokenRepository.findByTokenHash("hash-of-raw"))
                .thenReturn(Optional.of(token));
        when(userRepository.findById(USER_ID))
                .thenReturn(Optional.of(enabledUser()));

        Optional<RefreshTokenService.RotatedToken> rotated =
                refreshTokenService.rotate("raw");

        assertTrue(rotated.isPresent());
        assertEquals("new-raw-token", rotated.get().rawToken());
        assertEquals(USER_ID, rotated.get().user().getId());

        assertNotEquals(null, token.getRevokedAt(),
                "the used token must be revoked");
        assertNotEquals(null, token.getRotatedAt(),
                "the used token must be marked as rotated");
    }

    @Test
    void rotationOfAnUnknownTokenFails() {
        when(tokenGenerator.hash("unknown")).thenReturn("hash-of-unknown");
        when(tokenRepository.findByTokenHash("hash-of-unknown"))
                .thenReturn(Optional.empty());

        assertTrue(refreshTokenService.rotate("unknown").isEmpty());
    }

    @Test
    void revokingATokenMarksItAsRevoked() {
        RefreshToken token = new RefreshToken();
        token.setTokenHash("hash-of-raw");

        when(tokenGenerator.hash("raw")).thenReturn("hash-of-raw");
        when(tokenRepository.findByTokenHash("hash-of-raw"))
                .thenReturn(Optional.of(token));

        refreshTokenService.revoke("raw");

        assertNotEquals(null, token.getRevokedAt());
        verify(tokenRepository).save(token);
    }

    @Test
    void revokingAnUnknownTokenIsSafe() {
        when(tokenGenerator.hash("unknown")).thenReturn("hash-of-unknown");
        when(tokenRepository.findByTokenHash("hash-of-unknown"))
                .thenReturn(Optional.empty());

        refreshTokenService.revoke("unknown");

        verify(tokenRepository, never()).save(any(RefreshToken.class));
    }

    @Test
    void revokingAllTokensOfAnAccountMarksEveryOneOfThem() {
        RefreshToken first = new RefreshToken();
        RefreshToken second = new RefreshToken();
        second.setRevokedAt(Instant.now());

        when(tokenRepository.findAllByUserId(USER_ID))
                .thenReturn(List.of(first, second));

        refreshTokenService.revokeAllForUser(USER_ID);

        assertNotEquals(null, first.getRevokedAt());
        verify(tokenRepository).save(first);
        verify(tokenRepository, never()).save(second);
    }

    @Test
    void theDefaultLifetimeIsThirtyDays() {
        assertEquals(Duration.ofDays(30),
                RefreshTokenService.DEFAULT_TTL);
        assertFalse(refreshTokenService.getTokenTtl().isNegative());
    }
}
