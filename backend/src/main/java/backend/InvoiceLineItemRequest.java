package backend;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * A single invoice line supplied by the client.
 *
 * Quantity must be greater than zero and the unit price cannot be negative;
 * the deeper cross-checks (discount not exceeding the subtotal, and so on) are
 * enforced in {@link InvoiceService}.
 */
public record InvoiceLineItemRequest(

        @NotBlank(message = "Line description is required")
        @Size(max = 300, message = "Line description must be at most 300 characters")
        String description,

        @NotNull(message = "Line quantity is required")
        @DecimalMin(value = "0.0001", message = "Line quantity must be greater than 0")
        BigDecimal quantity,

        @NotNull(message = "Line unit price is required")
        @DecimalMin(value = "0.00", message = "Line unit price cannot be negative")
        BigDecimal unitPrice
) {

    public InvoiceLineItemRequest {
        description = trim(description);
    }

    private static String trim(String value) {
        return value == null ? null : value.trim();
    }
}
