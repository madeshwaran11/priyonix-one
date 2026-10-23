
package backend;

import java.util.Locale;
import java.util.Set;

/**
 * Canonical role model for PriyoniX One.
 *
 * Exactly two roles exist: {@link #ADMIN} and {@link #EMPLOYEE}. Accounts
 * created before this model was introduced carry the legacy role
 * {@code USER} (see the "Roles and permissions" section of the README), so
 * {@code USER} is treated as an alias of {@code EMPLOYEE}.
 *
 * Every read of a stored role is normalised through {@link #canonical(String)}.
 * That keeps legacy documents working without deleting or rewriting user
 * accounts, while the startup migration (RoleMigrationRunner) can persist the
 * canonical value once, idempotently.
 *
 * Unknown or missing roles fall back to EMPLOYEE: they carry the same
 * (non-privileged) permissions the legacy USER role had, so normalisation can
 * never escalate privileges.
 */
public final class UserRoles {

    /** Full access to administrative modules. */
    public static final String ADMIN = "ADMIN";

    /** Standard business access without administrative actions. */
    public static final String EMPLOYEE = "EMPLOYEE";

    /** Stored values that mean "administrator". */
    private static final Set<String> ADMIN_ALIASES = Set.of(
            "ADMIN",
            "SUPERADMIN",
            "SUPER_ADMIN",
            "OWNER",
            "ROOT");

    private UserRoles() {
    }

    /**
     * Maps any stored role to the canonical role model.
     *
     * @param rawRole the value persisted on the user document (may be null)
     * @return {@link #ADMIN} or {@link #EMPLOYEE}, never null
     */
    public static String canonical(String rawRole) {
        if (rawRole == null) {
            return EMPLOYEE;
        }

        String normalized = rawRole.trim().toUpperCase(Locale.ROOT);

        if (normalized.isEmpty()) {
            return EMPLOYEE;
        }

        return ADMIN_ALIASES.contains(normalized) ? ADMIN : EMPLOYEE;
    }

    public static boolean isAdmin(String rawRole) {
        return ADMIN.equals(canonical(rawRole));
    }

    public static boolean isEmployee(String rawRole) {
        return EMPLOYEE.equals(canonical(rawRole));
    }

    /**
     * True when the stored value differs from the canonical representation and
     * should therefore be persisted by the startup migration.
     */
    public static boolean needsMigration(String rawRole) {
        if (rawRole == null) {
            return true;
        }

        return !canonical(rawRole).equals(rawRole.trim());
    }

    /**
     * Parses a role requested by a client. Only the two canonical role names
     * are accepted; anything else is rejected so a crafted payload cannot
     * smuggle an unexpected role into the authentication flow.
     *
     * @return the canonical role, or null when the value is not a valid role
     */
    public static String parseRequestedRole(String rawRole) {
        if (rawRole == null || rawRole.isBlank()) {
            return null;
        }

        String normalized = rawRole.trim().toUpperCase(Locale.ROOT);

        if (ADMIN.equals(normalized) || EMPLOYEE.equals(normalized)) {
            return normalized;
        }

        return null;
    }
}
