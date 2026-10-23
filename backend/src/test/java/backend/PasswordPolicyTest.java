
package backend;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for the password complexity rules.
 */
class PasswordPolicyTest {

    @Test
    void strongPasswordIsAccepted() {
        assertTrue(PasswordPolicy.isValid("Sup3rSecret-Passw0rd!"));
    }

    @Test
    void shortPasswordIsRejected() {
        List<String> problems = PasswordPolicy.violations("Ab1!efgh");

        assertFalse(problems.isEmpty());
        assertTrue(problems.stream().anyMatch(
                problem -> problem.contains("at least 12")));
    }

    @Test
    void missingCharacterClassesAreReported() {
        List<String> problems = PasswordPolicy.violations("password");

        assertTrue(problems.stream().anyMatch(
                problem -> problem.contains("uppercase")));
        assertTrue(problems.stream().anyMatch(
                problem -> problem.contains("number")));
        assertTrue(problems.stream().anyMatch(
                problem -> problem.contains("special character")));
    }

    @Test
    void spacesAreRejected() {
        assertFalse(PasswordPolicy.isValid("Pass word1234!"));
    }

    @Test
    void emptyPasswordIsRejected() {
        assertFalse(PasswordPolicy.isValid(null));
        assertFalse(PasswordPolicy.isValid(""));
    }

    @Test
    void policyDescriptionMentionsTheMinimumLength() {
        assertTrue(PasswordPolicy.describe().contains("12"));
    }
}
