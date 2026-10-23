// Canonical role model for the PriyoniX One frontend.
//
// The backend owns the authoritative mapping (backend/UserRoles.java). The
// values below mirror it so the user interface never invents a role of its
// own and never derives permissions from a value the client sent.
//
// Exactly two roles exist: ADMIN and EMPLOYEE. The legacy role USER is an
// alias of EMPLOYEE, so a session that was stored before the two role model
// was introduced keeps working.

export const ROLE_ADMIN = "ADMIN";
export const ROLE_EMPLOYEE = "EMPLOYEE";

const ADMIN_ALIASES = new Set([
  "ADMIN",
  "SUPERADMIN",
  "SUPER_ADMIN",
  "OWNER",
  "ROOT",
]);

/**
 * Maps any stored role to the canonical role model. Unknown or missing
 * values fall back to EMPLOYEE, which is the least privileged role.
 *
 * @param {string | null | undefined} rawRole
 * @returns {"ADMIN" | "EMPLOYEE"}
 */
export function canonicalRole(rawRole) {
  if (typeof rawRole !== "string") {
    return ROLE_EMPLOYEE;
  }

  const normalized = rawRole.trim().toUpperCase();

  if (!normalized) {
    return ROLE_EMPLOYEE;
  }

  return ADMIN_ALIASES.has(normalized) ? ROLE_ADMIN : ROLE_EMPLOYEE;
}

/**
 * The landing route of a role. Administrators and employees get their own
 * workspace so a guard can keep them apart.
 */
export function homePathForRole(rawRole) {
  return canonicalRole(rawRole) === ROLE_ADMIN ? "/admin" : "/employee";
}

/** Human readable name of a role for the interface. */
export function roleLabel(rawRole) {
  return canonicalRole(rawRole) === ROLE_ADMIN ? "Administrator" : "Employee";
}
