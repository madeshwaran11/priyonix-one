
package backend;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import org.springframework.stereotype.Service;

/**
 * Issues, validates, rotates and revokes the refresh tokens that back the
 * "Remember me" option.
 *
 * Security properties:
 * <ul>
 *   <li>Tokens are generated with {@link SecureTokenGenerator} (256 bits of
 *       entropy) and only their SHA-256 hash is stored.</li>
 *   <li>The raw token is handed to the browser inside an HttpOnly cookie, so
 *       JavaScript can never read it.</li>
 *   <li>Every token is single use: using it revokes it and issues a
 *       successor, so a stolen cookie is only usable once and the theft
 *       becomes visible when the legitimate client refreshes as well.</li>
 *   <li>Tokens expire after a configurable window (30 days by default) and
 *       are removed from the database by the TTL index.</li>
 * </ul>
 */
@Service
public class RefreshTokenService {

    /** Default lifetime of a refresh token. */
    public static final Duration DEFAULT_TTL = Duration.ofDays(30);

    private final RefreshTokenRepository tokenRepository;
    private final UserRepository userRepository;
    private final SecureTokenGenerator tokenGenerator;
    private final Duration tokenTtl;

    public RefreshTokenService(
            RefreshTokenRepository tokenRepository,
            UserRepository userRepository,
            SecureTokenGenerator tokenGenerator,
            AuthProperties authProperties) {

        this.tokenRepository = tokenRepository;
        this.userRepository = userRepository;
        this.tokenGenerator = tokenGenerator;
        this.tokenTtl = authProperties.getRefreshTokenTtl();
    }

    /** The raw token is only ever held in memory and in the cookie. */
    public record IssuedToken(String rawToken, Instant expiresAt) {
    }

    /** A freshly rotated token together with the account it belongs to. */
    public record RotatedToken(
            String rawToken,
            Instant expiresAt,
            User user) {
    }

    /**
     * Issues a fresh refresh token for the given user. Any token that was
     * issued before for the same account is revoked, so only one device can
     * keep a remembered session per account.
     */
    public IssuedToken issue(User user) {
        Instant now = Instant.now();

        revokeAllForUser(user.getId());

        String rawToken = tokenGenerator.generate();

        RefreshToken token = new RefreshToken();
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
     * known, not revoked, not expired and belongs to an enabled account.
     */
    public Optional<RefreshToken> findValidToken(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return Optional.empty();
        }

        Optional<RefreshToken> found = tokenRepository
                .findByTokenHash(tokenGenerator.hash(rawToken));

        if (found.isEmpty()) {
            return Optional.empty();
        }

        RefreshToken token = found.get();

        if (!token.isUsable(Instant.now())) {
            return Optional.empty();
        }

        Optional<User> user = userRepository.findById(token.getUserId());

        if (user.isEmpty() || !user.get().isEnabled()) {
            return Optional.empty();
        }

        return Optional.of(token);
    }

    /**
     * Consumes a refresh token and issues its successor.
     *
     * @return the new token together with its account, or empty when the
     *         presented token is unknown, expired, revoked or already used
     */
    public Optional<RotatedToken> rotate(String rawToken) {
        Optional<RefreshToken> valid = findValidToken(rawToken);

        if (valid.isEmpty()) {
            return Optional.empty();
        }

        RefreshToken token = valid.get();
        Instant now = Instant.now();

        // Revoke before the successor is issued so a replay of the old token
        // can never produce a second successor.
        token.setRevokedAt(now);
        token.setRotatedAt(now);
        tokenRepository.save(token);

        Optional<User> user = userRepository.findById(token.getUserId());

        if (user.isEmpty()) {
            return Optional.empty();
        }

        IssuedToken successor = issue(user.get());

        return Optional.of(new RotatedToken(
                successor.rawToken(),
                successor.expiresAt(),
                user.get()));
    }

    /** Revokes a single token; unknown tokens are ignored. */
    public void revoke(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return;
        }

        tokenRepository.findByTokenHash(tokenGenerator.hash(rawToken))
                .ifPresent(token -> {
                    if (token.getRevokedAt() == null) {
                        token.setRevokedAt(Instant.now());
                        tokenRepository.save(token);
                    }
                });
    }

    /** Revokes every outstanding token of an account. */
    public void revokeAllForUser(String userId) {
        if (userId == null) {
            return;
        }

        tokenRepository.findAllByUserId(userId).forEach(token -> {
            if (token.getRevokedAt() == null) {
                token.setRevokedAt(Instant.now());
                tokenRepository.save(token);
            }
        });
    }

    /** Number of still usable tokens for an account (diagnostics/tests). */
    public long countActiveTokens(String userId) {
        return tokenRepository.countByUserIdAndRevokedAtIsNull(userId);
    }

    public Duration getTokenTtl() {
        return tokenTtl;
    }
}
