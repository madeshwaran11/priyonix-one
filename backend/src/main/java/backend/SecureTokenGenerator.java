
package backend;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

import org.springframework.stereotype.Component;

/**
 * Cryptographically secure token generation and hashing for password reset
 * links.
 *
 * Tokens are 256 bits of entropy from {@link SecureRandom}, encoded as
 * URL-safe Base64 so they can travel inside a query string. Only the SHA-256
 * hash of a token is ever persisted, so a leaked database cannot be used to
 * reset somebody else's password.
 */
@Component
public class SecureTokenGenerator {

    private static final int TOKEN_BYTES = 32;

    private final SecureRandom secureRandom = new SecureRandom();

    /** Generates a new random, URL-safe token. */
    public String generate() {
        byte[] bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * Hashes a raw token for storage and lookup. SHA-256 is appropriate here
     * because the token already carries full 256-bit entropy, so no
     * brute-force derivation is possible from the stored hash.
     */
    public String hash(String rawToken) {
        if (rawToken == null) {
            throw new IllegalArgumentException("Token is required");
        }

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(
                    rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(
                    "SHA-256 digest is not available", ex);
        }
    }
}
