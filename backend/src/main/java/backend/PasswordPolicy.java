
package backend;

import java.util.ArrayList;
import java.util.List;

/**
 * Password complexity rules shared by password reset and account seeding.
 *
 * The rules are intentionally explicit so the frontend can mirror them in the
 * reset form and the backend can return a precise, non-leaky validation
 * message.
 */
public final class PasswordPolicy {

    /** Minimum accepted length. */
    public static final int MIN_LENGTH = 12;

    /** Maximum accepted length (protects BCrypt against oversized input). */
    public static final int MAX_LENGTH = 128;

    private PasswordPolicy() {
    }

    /**
     * Returns every rule the password breaks. An empty list means the
     * password is acceptable.
     */
    public static List<String> violations(String password) {
        List<String> problems = new ArrayList<>();

        if (password == null || password.isEmpty()) {
            problems.add("Password is required.");
            return problems;
        }

        if (password.length() < MIN_LENGTH) {
            problems.add("Password must be at least " + MIN_LENGTH
                    + " characters long.");
        }

        if (password.length() > MAX_LENGTH) {
            problems.add("Password must be at most " + MAX_LENGTH
                    + " characters long.");
        }

        boolean hasUpper = false;
        boolean hasLower = false;
        boolean hasDigit = false;
        boolean hasSymbol = false;

        for (int i = 0; i < password.length(); i++) {
            char c = password.charAt(i);

            if (Character.isWhitespace(c)) {
                problems.add("Password must not contain spaces.");
                break;
            }

            if (Character.isUpperCase(c)) {
                hasUpper = true;
            } else if (Character.isLowerCase(c)) {
                hasLower = true;
            } else if (Character.isDigit(c)) {
                hasDigit = true;
            } else {
                hasSymbol = true;
            }
        }

        if (!hasUpper) {
            problems.add("Password must contain an uppercase letter.");
        }

        if (!hasLower) {
            problems.add("Password must contain a lowercase letter.");
        }

        if (!hasDigit) {
            problems.add("Password must contain a number.");
        }

        if (!hasSymbol) {
            problems.add("Password must contain a special character.");
        }

        return problems;
    }

    public static boolean isValid(String password) {
        return violations(password).isEmpty();
    }

    /** Human readable summary of the policy, used in emails and the UI. */
    public static String describe() {
        return "At least " + MIN_LENGTH
                + " characters, including an uppercase letter, a lowercase"
                + " letter, a number and a special character.";
    }
}
