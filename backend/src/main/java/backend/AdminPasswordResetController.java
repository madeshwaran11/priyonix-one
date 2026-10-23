
package backend;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.Map;

import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Explicit, secret-guarded admin password reset.
 *
 * Recovery path for the case where the stored admin password no longer
 * matches ADMIN_PASSWORD and normal login (thus JWT issuance) is
 * impossible. The endpoint requires the X-Admin-Reset-Token header to
 * equal the server-side ADMIN_RESET_TOKEN environment variable, compared
 * in constant time. When ADMIN_RESET_TOKEN is not configured the
 * endpoint always answers 401, so it is unusable by default. Only the
 * configured ADMIN_EMAIL account can ever be modified, and no secret,
 * hash or credential is ever logged or returned.
 */
@RestController
@RequestMapping("/api/auth/admin")
public class AdminPasswordResetController {

    private static final int MIN_PASSWORD_LENGTH = 12;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final Environment environment;

    public AdminPasswordResetController(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            Environment environment) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.environment = environment;
    }

    @PostMapping("/reset-password")
    public ResponseEntity<Map<String, String>> resetPassword(
            @RequestHeader(value = "X-Admin-Reset-Token",
                    required = false) String resetToken,
            @RequestBody ResetRequest request) {

        String configuredToken = environment.getProperty("ADMIN_RESET_TOKEN");
        String configuredEmail = environment.getProperty("ADMIN_EMAIL");

        // Identical generic 401 for missing header, wrong token and
        // unconfigured endpoint: nothing for an attacker to probe.
        if (!tokenMatches(configuredToken, resetToken)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of(
                            "status", "ERROR",
                            "message", "Invalid or missing reset token"));
        }

        if (configuredEmail == null || configuredEmail.isBlank()
                || request == null || request.email() == null
                || request.password() == null
                || request.password().length() < MIN_PASSWORD_LENGTH) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of(
                            "status", "ERROR",
                            "message", "Email and a password of at least "
                                    + MIN_PASSWORD_LENGTH
                                    + " characters are required"));
        }

        String targetEmail = configuredEmail.trim()
                .toLowerCase(Locale.ROOT);

        // The reset token only governs the configured admin account.
        if (!targetEmail.equals(
                request.email().trim().toLowerCase(Locale.ROOT))) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of(
                            "status", "ERROR",
                            "message", "This endpoint can only reset the"
                                    + " configured admin account"));
        }

        User admin = userRepository.findByEmailIgnoreCase(targetEmail)
                .orElseGet(() -> {
                    User created = new User();
                    created.setEmail(targetEmail);
                    return created;
                });

        admin.setPassword(passwordEncoder.encode(request.password()));
        admin.setRole("ADMIN");
        admin.setEnabled(true);
        userRepository.save(admin);

        System.out.println(
            "Admin password reset via ADMIN_RESET_TOKEN completed for the"
            + " configured admin account.");
        return ResponseEntity.ok(Map.of(
                "status", "SUCCESS",
                "message", "Admin password has been reset"));
    }

    private boolean tokenMatches(String configured, String provided) {
        if (configured == null || configured.isBlank()
                || provided == null || provided.isBlank()) {
            return false;
        }
        return MessageDigest.isEqual(
                configured.getBytes(StandardCharsets.UTF_8),
                provided.getBytes(StandardCharsets.UTF_8));
    }

    public record ResetRequest(String email, String password) {}
}
