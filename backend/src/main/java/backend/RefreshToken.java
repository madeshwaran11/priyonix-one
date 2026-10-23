
package backend;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Refresh token document stored in the collection "refresh_tokens".
 *
 * The raw token is only ever held inside the HttpOnly cookie that is sent to
 * the browser; the database keeps nothing but its SHA-256 hash, so a leaked
 * database cannot be used to impersonate a signed-in user.
 *
 * The TTL index below removes the document automatically once it expires.
 * The service additionally enforces the expiry, the revocation and the
 * single use of every token, so the behaviour does not depend on the
 * background task that removes expired documents.
 */
@Document(collection = "refresh_tokens")
public class RefreshToken {

    @Id
    private String id;

    /** SHA-256 hash of the issued token (unique). */
    @Indexed(unique = true)
    private String tokenHash;

    /** Id of the user the token was issued for. */
    private String userId;

    /** Email of the user the token was issued for. */
    private String userEmail;

    /** Instant after which the token is no longer valid. */
    @Indexed(expireAfter = "0s")
    private Instant expiresAt;

    /** Instant the token was issued. */
    private Instant createdAt;

    /** Instant the token was revoked (sign out, password change, reuse). */
    private Instant revokedAt;

    /** Instant the token was replaced by a successor. */
    private Instant rotatedAt;

    public RefreshToken() {
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public void setTokenHash(String tokenHash) {
        this.tokenHash = tokenHash;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getUserEmail() {
        return userEmail;
    }

    public void setUserEmail(String userEmail) {
        this.userEmail = userEmail;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }

    public void setRevokedAt(Instant revokedAt) {
        this.revokedAt = revokedAt;
    }

    public Instant getRotatedAt() {
        return rotatedAt;
    }

    public void setRotatedAt(Instant rotatedAt) {
        this.rotatedAt = rotatedAt;
    }

    public boolean isExpired(Instant now) {
        return expiresAt == null || !expiresAt.isAfter(now);
    }

    public boolean isRevoked() {
        return revokedAt != null;
    }

    public boolean isUsable(Instant now) {
        return !isRevoked() && !isExpired(now);
    }
}
