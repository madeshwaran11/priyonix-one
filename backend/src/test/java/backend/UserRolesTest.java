
package backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for the canonical role model.
 */
class UserRolesTest {

    @Test
    void adminStaysAdmin() {
        assertEquals(UserRoles.ADMIN, UserRoles.canonical("ADMIN"));
        assertEquals(UserRoles.ADMIN, UserRoles.canonical("admin"));
        assertEquals(UserRoles.ADMIN, UserRoles.canonical("  Admin  "));
    }

    @Test
    void legacyUserRoleMapsToEmployee() {
        assertEquals(UserRoles.EMPLOYEE, UserRoles.canonical("USER"));
        assertEquals(UserRoles.EMPLOYEE, UserRoles.canonical("user"));
        assertEquals(UserRoles.EMPLOYEE, UserRoles.canonical("Employee"));
    }

    @Test
    void unknownOrMissingRoleFallsBackToEmployee() {
        assertEquals(UserRoles.EMPLOYEE, UserRoles.canonical(null));
        assertEquals(UserRoles.EMPLOYEE, UserRoles.canonical(""));
        assertEquals(UserRoles.EMPLOYEE, UserRoles.canonical("   "));
        assertEquals(UserRoles.EMPLOYEE, UserRoles.canonical("HR_MANAGER"));
    }

    @Test
    void rolePredicatesUseCanonicalValues() {
        assertTrue(UserRoles.isAdmin("ADMIN"));
        assertTrue(UserRoles.isAdmin("SUPERADMIN"));
        assertFalse(UserRoles.isAdmin("USER"));

        assertTrue(UserRoles.isEmployee("USER"));
        assertTrue(UserRoles.isEmployee(null));
        assertFalse(UserRoles.isEmployee("ADMIN"));
    }

    @Test
    void onlyTheTwoCanonicalRolesAreAcceptedFromClients() {
        assertEquals(UserRoles.ADMIN,
                UserRoles.parseRequestedRole("admin"));
        assertEquals(UserRoles.EMPLOYEE,
                UserRoles.parseRequestedRole(" EMPLOYEE "));
        assertNull(UserRoles.parseRequestedRole("USER"));
        assertNull(UserRoles.parseRequestedRole("SUPERADMIN"));
        assertNull(UserRoles.parseRequestedRole(""));
        assertNull(UserRoles.parseRequestedRole(null));
    }

    @Test
    void migrationIsOnlyNeededForNonCanonicalValues() {
        assertTrue(UserRoles.needsMigration("USER"));
        assertTrue(UserRoles.needsMigration(null));
        assertTrue(UserRoles.needsMigration("admin"));

        assertFalse(UserRoles.needsMigration("ADMIN"));
        assertFalse(UserRoles.needsMigration("EMPLOYEE"));
    }
}
