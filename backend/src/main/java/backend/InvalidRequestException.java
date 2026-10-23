package backend;

/**
 * Thrown when a request passes bean validation but breaks a business rule
 * (for example an unsupported employment status).
 * Mapped to HTTP 400 by {@link ApiExceptionHandler}.
 */
public class InvalidRequestException extends RuntimeException {

    public InvalidRequestException(String message) {
        super(message);
    }
}
