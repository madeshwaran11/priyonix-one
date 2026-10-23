
package backend;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * Issues, validates and consumes password reset tokens.
 *
 * Security properties:
 * <ul>
 *   <li>Tokens are generated with {@link SecureTokenGenerator} (256 bits of
 *       entropy) and only their SHA-256 hash is stored.</li>
 *   <li>Tokens expire after a short window (15 minutes by default).</li>
 *   <li>A token is single use: it is marked as consumed before the password
 *       changes and every outstanding token of the account is removed
 *       afterwards, so an old link can never be replayed.</li>
 *   <li>New passwords are always stored as a BCrypt hash.</li>
 *   <li>A reset also revokes every refresh token of the account, so a
 *       remembered session cannot survive a password change.</li>
 * </ul>
 */
@Service
public class PasswordResetService {

    /** Default lifetime of a reset link. */
    public static final Duration DEFAULT_TOKEN_TTL = Duration.ofMinutes(15);

    private final PasswordResetTokenRepository tokenRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final SecureTokenGenerator tokenGenerator;
    private final RefreshTokenService refreshTokenService;
    private final Duration tokenTtl;

    public PasswordResetService(
            PasswordResetTokenRepository tokenRepository,
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            SecureTokenGenerator tokenGenerator,
            RefreshTokenService refreshTokenService,
            AuthProperties authProperties) {

        this.tokenRepository = tokenRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenGenerator = tokenGenerator;
        this.refreshTokenService = refreshTokenService;
        this.tokenTtl = authProperties.getResetTokenTtl();
    }

    /** The raw token is only ever held in memory and in the emailed link. */
    public record IssuedToken(String rawToken, Instant expiresAt) {
    }

    /**
     * Issues a fresh token for the given user and removes any token that was
     * issued before, so only the newest link works.
     */
    public IssuedToken issue(User user) {
        Instant now = Instant.now();

        if (user.getId() != null) {
            tokenRepository.deleteAllByUserId(user.getId());
        }

        String rawToken = tokenGenerator.generate();

        PasswordResetToken token = new PasswordResetToken();
        token.setTokenHash(tokenGenerator.hash(rawToken));
        token.setUserId(user.getId());
        token.setUserEmail(user.getEmail());
        token.setCreatedAt(now);
        token.setExpiresAt(now.plus(tokenTtl));

        tokenRepository.save(token);

        return new IssuedToken(rawToken, token.getExpiresAt());
    }

    /**
     * Validates a raw token and returns the matching document when it is
     * unknown-but-unconsumed, unexpired and belongs to an enabled account.
     */
    public Optional<PasswordResetToken> findValidToken(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return Optional.empty();
        }

        Optional<PasswordResetToken> found = tokenRepository
                .findByTokenHash(tokenGenerator.hash(rawToken));

        if (found.isEmpty()) {
            return Optional.empty();
        }

        PasswordResetToken token = found.get();

        if (token.isUsed() || token.isExpired(Instant.now())) {
            return Optional.empty();
        }

        return Optional.of(token);
    }

    /**
     * Consumes a reset token and applies the new password.
     *
     * @throws InvalidResetTokenException when the token is unknown, expired or
     *         already used
     * @throws InvalidRequestException when the new password violates the
     *         password policy or the confirmation does not match
     */
    public void resetPassword(
            String rawToken,
            String newPassword,
            String confirmPassword) {

        List<String> violations = PasswordPolicy.violations(newPassword);

        if (!violations.isEmpty()) {
            throw new InvalidRequestException(String.join(" ", violations));
        }

        if (newPassword == null || !newPassword.equals(confirmPassword)) {
            throw new InvalidRequestException(
                    "The new password and its confirmation do not match.");
        }

        PasswordResetToken token = findValidToken(rawToken)
                .orElseThrow(() -> new InvalidResetTokenException(
                        "This password reset link is invalid or has expired."));

        User user = userRepository.findById(token.getUserId())
                .orElseThrow(() -> new InvalidResetTokenException(
                        "This password reset link is invalid or has expired."));

        if (!user.isEnabled()) {
            throw new InvalidResetTokenException(
                    "This password reset link is invalid or has expired.");
        }

        // Consume the token before the password changes so a concurrent
        // request can never reuse the same link.
        token.setUsedAt(Instant.now());
        tokenRepository.save(token);

        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        // Every remaining token of this account dies with the reset.
        tokenRepository.deleteAllByUserId(user.getId());

        // A password change also ends every remembered session, so a stolen
        // device cannot keep using the old session.
        refreshTokenService.revokeAllForUser(user.getId());
    }

    /** Number of still usable tokens for an account (diagnostics/tests). */
    public long countActiveTokens(String userId) {
        return tokenRepository.countByUserIdAndUsedAtIsNull(userId);
    }

    public Duration getTokenTtl() {
        return tokenTtl;
    }
}
