package backend;

/**
 * Thrown when a request conflicts with the current state of a resource in a
 * way that is not a uniqueness violation — for example deleting a project
 * that still contains tasks.
 *
 * Mapped to HTTP 409 by {@link ApiExceptionHandler}.
 */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
