package backend;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Payload for creating and updating a business expense.
 *
 * The status is intentionally absent: every expense is created PENDING and can
 * only be approved, rejected or marked reimbursed by an administrator through
 * the dedicated status endpoint.
 */
public record ExpenseRequest(

        @NotBlank(message = "Expense title is required")
        @Size(max = 200, message = "Expense title must be at most 200 characters")
        String title,

        @Size(max = 40, message = "Category must be at most 40 characters")
        String category,

        @NotNull(message = "Amount is required")
        @DecimalMin(value = "0.01", message = "Amount must be greater than 0")
        BigDecimal amount,

        @NotNull(message = "Expense date is required")
        LocalDate expenseDate,

        @Size(max = 160, message = "Vendor must be at most 160 characters")
        String vendor,

        @Size(max = 30, message = "Payment method must be at most 30 characters")
        String paymentMethod,

        @Size(max = 120, message = "Receipt reference must be at most 120 characters")
        String receiptReference,

        @Size(max = 2000, message = "Notes must be at most 2000 characters")
        String notes
) {

    public ExpenseRequest {
        title = trim(title);
        category = trim(category);
        vendor = trim(vendor);
        paymentMethod = trim(paymentMethod);
        receiptReference = trim(receiptReference);
        notes = trim(notes);
    }

    private static String trim(String value) {
        return value == null ? null : value.trim();
    }
}
