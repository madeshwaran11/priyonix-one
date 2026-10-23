
package backend;

import org.springframework.boot.CommandLineRunner;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * Creates the initial admin account on first startup.
 *
 * Existing accounts are NEVER silently modified. If the stored password no
 * longer matches ADMIN_PASSWORD (for example after the environment variable
 * was changed), an operator can explicitly request a one-time reset by
 * starting the application with ADMIN_RESET_PASSWORD=true, or at runtime via
 * POST /api/auth/admin/reset-password guarded by ADMIN_RESET_TOKEN. Both
 * mechanisms are server-side only; neither can be triggered by an
 * unauthenticated public request.
 */
@Component
public class AdminSetup implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final Environment environment;

    public AdminSetup(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            Environment environment) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.environment = environment;
    }

    @Override
    public void run(String... args) {
        String email = environment.getProperty("ADMIN_EMAIL");
        String password = environment.getProperty("ADMIN_PASSWORD");
        boolean resetRequested = "true".equalsIgnoreCase(
                environment.getProperty("ADMIN_RESET_PASSWORD"));

        // Each skip reason is logged distinctly so the startup log states
        // exactly why initialization did or did not run. No secret values
        // are ever printed.
        if (email == null || email.isBlank()) {
            System.out.println(
                "Admin setup skipped: ADMIN_EMAIL is not set.");
            return;
        }

        if (password == null || password.isBlank()) {
            System.out.println(
                "Admin setup skipped: ADMIN_PASSWORD is not set.");
            return;
        }

        if (password.length() < 12) {
            System.out.println(
                "Admin setup skipped: ADMIN_PASSWORD is shorter than the"
                + " required 12 characters.");
            return;
        }

        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);

        userRepository.findByEmailIgnoreCase(normalizedEmail)
                .ifPresentOrElse(
                        existing -> handleExistingAccount(
                                existing, password, resetRequested),
                        () -> createAdmin(normalizedEmail, password));
    }

    private void createAdmin(String normalizedEmail, String rawPassword) {
        User admin = new User();
        admin.setEmail(normalizedEmail);
        admin.setPassword(passwordEncoder.encode(rawPassword));
        admin.setRole("ADMIN");
        admin.setEnabled(true);

        userRepository.save(admin);
        System.out.println("Initial admin account created.");
    }

    private void handleExistingAccount(
            User existing,
            String rawPassword,
            boolean resetRequested) {

        if (!resetRequested) {
            // Diagnostic only: reports a boolean, never any secret value.
            boolean matches = passwordEncoder.matches(
                    rawPassword, existing.getPassword());
            System.out.println(
                "Admin account already exists; no changes made."
                + " Stored password matches configured ADMIN_PASSWORD: "
                + matches + ".");
            if (!matches) {
                System.out.println(
                    "Hint: reset the configured admin password either with"
                    + " the ADMIN_RESET_PASSWORD=true startup flag or via"
                    + " POST /api/auth/admin/reset-password using the"
                    + " X-Admin-Reset-Token header.");
            }
            return;
        }

        existing.setPassword(passwordEncoder.encode(rawPassword));
        existing.setRole("ADMIN");
        existing.setEnabled(true);
        userRepository.save(existing);
        System.out.println(
            "ADMIN_RESET_PASSWORD=true: password reset for the configured"
            + " admin account. Remove the flag on the next restart.");
    }
}
