
package backend;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Password reset token document stored in the collection
 * "password_reset_tokens".
 *
 * Only the SHA-256 hash of the issued token is stored (never the token
 * itself). The document expires after {@link PasswordResetService}'s TTL and
 * is deleted as soon as the password is reset, which makes every issued link
 * single use. The TTL index below is honoured when MongoDB index creation is
 * enabled; the service enforces the expiry logically in every case.
 */
@Document(collection = "password_reset_tokens")
public class PasswordResetToken {

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

    /** Instant the token was consumed; a consumed token can never be reused. */
    private Instant usedAt;

    public PasswordResetToken() {
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

    public Instant getUsedAt() {
        return usedAt;
    }

    public void setUsedAt(Instant usedAt) {
        this.usedAt = usedAt;
    }

    public boolean isExpired(Instant now) {
        return expiresAt == null || !expiresAt.isAfter(now);
    }

    public boolean isUsed() {
        return usedAt != null;
    }
}
