package backend;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Payment representation returned by the API, enriched with the parent
 * invoice number and client name so payment lists are human-readable without
 * extra lookups.
 */
public record PaymentResponse(
        String id,
        String invoiceId,
        String invoiceNumber,
        String clientId,
        String clientName,
        BigDecimal amount,
        LocalDate paymentDate,
        String method,
        String referenceNumber,
        String notes,
        String recordedBy,
        Instant createdAt,
        Instant updatedAt) {
}
