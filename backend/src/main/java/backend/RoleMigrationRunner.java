
package backend;

import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * Persists the canonical role for accounts created before the two role
 * model was introduced.
 *
 * Legacy documents store the role {@code USER}; the canonical model knows
 * only {@code ADMIN} and {@code EMPLOYEE}. The mapping is applied when a role
 * is read (see {@link UserRoles#canonical(String)}), so this runner is only
 * housekeeping: it rewrites the {@code role} field of accounts whose stored
 * value is not canonical yet.
 *
 * The runner never creates, deletes or disables an account, and it can be
 * switched off completely with {@code ROLE_MIGRATION_ENABLED=false}.
 */
@Component
public class RoleMigrationRunner implements CommandLineRunner {

    private final UserRepository userRepository;
    private final AuthProperties authProperties;

    public RoleMigrationRunner(
            UserRepository userRepository,
            AuthProperties authProperties) {
        this.userRepository = userRepository;
        this.authProperties = authProperties;
    }

    @Override
    public void run(String... args) {
        if (!authProperties.isRoleMigrationEnabled()) {
            System.out.println(
                    "Role migration skipped: ROLE_MIGRATION_ENABLED is false.");
            return;
        }

        int migrated = 0;

        for (User user : userRepository.findAll()) {
            if (!UserRoles.needsMigration(user.getRole())) {
                continue;
            }

            user.setRole(UserRoles.canonical(user.getRole()));
            userRepository.save(user);
            migrated++;
        }

        if (migrated > 0) {
            System.out.println(
                    "Role migration: " + migrated
                    + " account(s) updated to the canonical ADMIN/EMPLOYEE"
                    + " roles.");
        }
    }
}
