
package backend;

import java.util.Locale;
import java.util.Optional;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /** Outcome of an authentication attempt. */
    public enum Status {
        /** Credentials are valid and the role matches the expectation. */
        SUCCESS,
        /** Unknown, disabled or wrong-password account. */
        INVALID_CREDENTIALS,
        /** Credentials are valid but the account has another role. */
        ROLE_MISMATCH
    }

    /** Result of an authentication attempt. */
    public record AuthenticationResult(Status status, User user) {
    }

    /**
     * Authenticates an email/password pair case-insensitively.
     *
     * Returns the enabled user whose stored BCrypt hash matches the raw
     * password, or empty for missing, disabled or wrong-password accounts.
     * The HTTP response is always a generic 401; for server-side debugging
     * a single boolean-only diagnostic line is printed (never the email,
     * password, or hash).
     */
    public Optional<User> authenticate(String email, String rawPassword) {
        AuthenticationResult result = authenticate(email, rawPassword, null);

        if (result.status() != Status.SUCCESS) {
            return Optional.empty();
        }

        return Optional.of(result.user());
    }

    /**
     * Authenticates an email/password pair and optionally verifies that the
     * account really carries the requested role.
     *
     * The expected role is never taken from the request on trust: it is
     * compared with the role stored in MongoDB for the account, so selecting
     * "Administrator" in the user interface can never grant administrator
     * permissions.
     *
     * @param expectedRole {@code ADMIN}, {@code EMPLOYEE} or null when the
     *                     caller does not care about the role
     */
    public AuthenticationResult authenticate(
            String email,
            String rawPassword,
            String expectedRole) {

        if (email == null || rawPassword == null
                || email.isBlank() || rawPassword.isBlank()) {
            System.out.println(
                "[auth] login attempt rejected: email or password missing.");
            return new AuthenticationResult(
                    Status.INVALID_CREDENTIALS, null);
        }

        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);

        if (!normalizedEmail.contains("@")
                || normalizedEmail.length() > 254) {
            System.out.println(
                "[auth] login attempt rejected: malformed email.");
            return new AuthenticationResult(
                    Status.INVALID_CREDENTIALS, null);
        }

        Optional<User> found = userRepository
                .findByEmailIgnoreCase(normalizedEmail);

        if (found.isEmpty()) {
            System.out.println(
                "[auth] login diagnostics: user found=false");
            return new AuthenticationResult(
                    Status.INVALID_CREDENTIALS, null);
        }

        User user = found.get();
        boolean enabled = user.isEnabled();
        boolean hasStoredHash = user.getPassword() != null
                && !user.getPassword().isBlank();
        boolean passwordMatches = hasStoredHash
                && passwordEncoder.matches(rawPassword, user.getPassword());

        // Boolean-only diagnostic: tells the operator exactly which check
        // failed without exposing any secret value.
        System.out.println(
                "[auth] login diagnostics: user found=true, enabled=" + enabled
                + ", has stored hash=" + hasStoredHash
                + ", password matches=" + passwordMatches);

        if (!enabled || !hasStoredHash || !passwordMatches) {
            return new AuthenticationResult(
                    Status.INVALID_CREDENTIALS, null);
        }

        String actualRole = UserRoles.canonical(user.getRole());

        if (expectedRole != null
                && !expectedRole.equals(actualRole)) {
            System.out.println(
                    "[auth] login rejected: account role does not match the"
                    + " requested sign-in option.");
            return new AuthenticationResult(Status.ROLE_MISMATCH, null);
        }

        return new AuthenticationResult(Status.SUCCESS, user);
    }
}
