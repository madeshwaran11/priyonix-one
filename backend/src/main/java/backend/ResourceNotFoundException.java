package backend;

/**
 * Thrown when a requested employee (or other resource) does not exist.
 * Mapped to HTTP 404 by {@link ApiExceptionHandler}.
 */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }
}
