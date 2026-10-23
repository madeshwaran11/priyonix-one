package backend;

/**
 * Thrown when a password reset token is missing, unknown, already consumed or
 * expired. Mapped to HTTP 400 by {@link ApiExceptionHandler}.
 *
 * The message never reveals whether a token ever existed for a given account,
 * so an attacker cannot use the reset endpoint to probe for valid links.
 */
public class InvalidResetTokenException extends RuntimeException {

    public InvalidResetTokenException(String message) {
        super(message);
    }
}
