package backend;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Payload for recording or updating a payment against an invoice.
 *
 * Card numbers / bank credentials are never accepted here — only a
 * non-sensitive operator reference. The service rejects overpayments and
 * duplicate references.
 */
public record PaymentRequest(

        @NotBlank(message = "Invoice is required")
        String invoiceId,

        @NotNull(message = "Payment amount is required")
        @DecimalMin(value = "0.01", message = "Payment amount must be greater than 0")
        BigDecimal amount,

        @NotNull(message = "Payment date is required")
        LocalDate paymentDate,

        @Size(max = 30, message = "Payment method must be at most 30 characters")
        String method,

        @Size(max = 120, message = "Reference number must be at most 120 characters")
        String referenceNumber,

        @Size(max = 2000, message = "Notes must be at most 2000 characters")
        String notes
) {

    public PaymentRequest {
        invoiceId = trim(invoiceId);
        method = trim(method);
        referenceNumber = trim(referenceNumber);
        notes = trim(notes);
    }

    private static String trim(String value) {
        return value == null ? null : value.trim();
    }
}
