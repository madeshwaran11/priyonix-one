package backend;

import java.math.BigDecimal;

/**
 * A single billing line as returned by the API, including the derived amount
 * (quantity × unit price, rounded HALF_UP to 2 decimals).
 */
public record InvoiceLineItemResponse(
        String description,
        BigDecimal quantity,
        BigDecimal unitPrice,
        BigDecimal amount) {
}
