package backend;

/**
 * Thrown when a create/update would violate a uniqueness rule
 * (for example a duplicate employee ID or work email).
 * Mapped to HTTP 409 by {@link ApiExceptionHandler}.
 */
public class DuplicateResourceException extends RuntimeException {

    public DuplicateResourceException(String message) {
        super(message);
    }
}
