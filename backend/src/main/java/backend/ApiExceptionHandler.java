package backend;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Translates domain exceptions into consistent JSON error bodies.
 *
 * The response shape ({@code status}, {@code message}) mirrors the existing
 * auth endpoints so the frontend can handle every error the same way. No
 * internal detail, credential or stack trace is ever returned.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> handleAccessDenied(RuntimeException ex) {
        return build(HttpStatus.FORBIDDEN, "You do not have permission for this operation", null);
    }

    @ExceptionHandler(org.springframework.dao.OptimisticLockingFailureException.class)
    public ResponseEntity<Map<String, Object>> handleStaleUpdate(RuntimeException ex) {
        return build(HttpStatus.CONFLICT, "This record changed. Refresh it before trying again", null);
    }

    @ExceptionHandler(org.springframework.data.mongodb.MongoTransactionException.class)
    public ResponseEntity<Map<String, Object>> handleTransactionConflict(RuntimeException ex) {
        return build(HttpStatus.CONFLICT, "The operation could not commit. Refresh and retry", null);
    }

    @ExceptionHandler(org.springframework.web.multipart.MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, Object>> handleUploadTooLarge(RuntimeException ex) {
        return build(HttpStatus.PAYLOAD_TOO_LARGE, "Attachments must be 5 MB or smaller", null);
    }

    @ExceptionHandler({org.springframework.web.bind.MissingServletRequestParameterException.class,
            org.springframework.web.multipart.support.MissingServletRequestPartException.class,
            org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class})
    public ResponseEntity<Map<String, Object>> handleMissingInput(Exception ex) {
        return build(HttpStatus.BAD_REQUEST, "A required parameter is missing or invalid", null);
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(
            ResourceNotFoundException ex) {
        return build(HttpStatus.NOT_FOUND, ex.getMessage(), null);
    }

    @ExceptionHandler(DuplicateResourceException.class)
    public ResponseEntity<Map<String, Object>> handleDuplicate(
            DuplicateResourceException ex) {
        return build(HttpStatus.CONFLICT, ex.getMessage(), null);
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<Map<String, Object>> handleConflict(
            ConflictException ex) {
        return build(HttpStatus.CONFLICT, ex.getMessage(), null);
    }

    /**
     * Backstop for the unique Mongo indexes (employee ID, work email,
     * project code, task code) in case two concurrent requests pass the
     * service-level check.
     */
    @ExceptionHandler(DuplicateKeyException.class)
    public ResponseEntity<Map<String, Object>> handleDuplicateKey(
            DuplicateKeyException ex) {
        return build(HttpStatus.CONFLICT,
                "A record with the same unique value already exists", null);
    }

    @ExceptionHandler(InvalidRequestException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidRequest(
            InvalidRequestException ex) {
        return build(HttpStatus.BAD_REQUEST, ex.getMessage(), null);
    }

    /**
     * Malformed or unparseable JSON bodies (for example an invalid date)
     * are reported as a clean 400 instead of a Spring stack-trace body.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> handleUnreadable(
            HttpMessageNotReadableException ex) {
        return build(HttpStatus.BAD_REQUEST,
                "Request body is malformed or contains an invalid value",
                null);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(
            MethodArgumentNotValidException ex) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.putIfAbsent(error.getField(), error.getDefaultMessage());
        }

        return build(HttpStatus.BAD_REQUEST,
                "Validation failed", fieldErrors);
    }

    private ResponseEntity<Map<String, Object>> build(
            HttpStatus status, String message, Map<String, String> errors) {

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "ERROR");
        body.put("message", message == null ? status.getReasonPhrase() : message);
        body.put("timestamp", Instant.now().toString());
        if (errors != null && !errors.isEmpty()) {
            body.put("errors", errors);
        }

        return ResponseEntity.status(status).body(body);
    }
}
