// The password policy of PriyoniX One.
//
// The rules mirror backend/PasswordPolicy.java exactly. The backend stays
// the authority and validates every password again; this copy only lets the
// interface explain the requirements before the request is sent.

export const PASSWORD_MIN_LENGTH = 12;
export const PASSWORD_MAX_LENGTH = 128;

/**
 * Checks a single rule.
 *
 * @param {string} password
 * @param {{id: string, label: string}} rule
 * @returns {boolean}
 */
export function satisfiesRule(password, rule) {
  const value = password || "";

  switch (rule.id) {
    case "length":
      return (
        value.length >= PASSWORD_MIN_LENGTH &&
        value.length <= PASSWORD_MAX_LENGTH
      );
    case "upper":
      return /[A-Z]/.test(value);
    case "lower":
      return /[a-z]/.test(value);
    case "digit":
      return /[0-9]/.test(value);
    case "symbol":
      return /[^A-Za-z0-9]/.test(value);
    case "spaces":
      return !/\s/.test(value);
    default:
      return false;
  }
}

/** The rules that are shown next to the password field. */
export const PASSWORD_RULES = [
  {
    id: "length",
    label: `At least ${PASSWORD_MIN_LENGTH} characters`,
  },
  { id: "upper", label: "An uppercase letter" },
  { id: "lower", label: "A lowercase letter" },
  { id: "digit", label: "A number" },
  { id: "symbol", label: "A special character" },
  { id: "spaces", label: "No spaces" },
];

/**
 * Every rule the password breaks.
 *
 * @param {string} password
 * @returns {string[]} the labels of the broken rules
 */
export function passwordViolations(password) {
  return PASSWORD_RULES.filter((rule) => !satisfiesRule(password, rule)).map(
    (rule) => rule.label
  );
}

/** True when the password satisfies every rule. */
export function isStrongEnough(password) {
  return passwordViolations(password).length === 0;
}
