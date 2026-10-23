package backend;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Payload for an administrative expense status decision.
 *
 * Allowed values (validated in {@link ExpenseService}):
 * PENDING, APPROVED, REJECTED, REIMBURSED.
 */
public record ExpenseStatusRequest(

        @NotBlank(message = "Status is required")
        @Size(max = 30, message = "Status must be at most 30 characters")
        String status
) {

    public ExpenseStatusRequest {
        status = status == null ? null : status.trim();
    }
}
