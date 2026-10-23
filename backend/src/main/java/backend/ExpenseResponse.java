package backend;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Expense representation returned by the API, including the approval trail.
 */
public record ExpenseResponse(
        String id,
        String title,
        String category,
        BigDecimal amount,
        LocalDate expenseDate,
        String vendor,
        String paymentMethod,
        String receiptReference,
        String notes,
        String status,
        String approvedBy,
        Instant approvedAt,
        String createdBy,
        Instant createdAt,
        Instant updatedAt) {
}
