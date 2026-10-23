package backend;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Invoice representation returned by the API.
 *
 * All money figures are computed server-side from the stored line items and
 * the recorded payments, so the client never has to (and never should)
 * calculate them. {@code status} is the effective status: the stored issue
 * state (DRAFT) or the derived SENT / PARTIALLY_PAID / PAID / OVERDUE state.
 * {@code overdue} is exposed separately so a partially paid invoice that is
 * past due can still be highlighted.
 */
public record InvoiceResponse(
        String id,
        String invoiceNumber,
        String clientId,
        String clientName,
        String clientCompany,
        String projectId,
        String projectName,
        String projectCode,
        LocalDate issueDate,
        LocalDate dueDate,
        String status,
        boolean overdue,
        List<InvoiceLineItemResponse> lineItems,
        BigDecimal subtotal,
        BigDecimal discount,
        BigDecimal taxableAmount,
        BigDecimal taxRate,
        BigDecimal taxAmount,
        BigDecimal total,
        BigDecimal amountPaid,
        BigDecimal balance,
        int paymentCount,
        String notes,
        String createdBy,
        Instant createdAt,
        Instant updatedAt) {
}
