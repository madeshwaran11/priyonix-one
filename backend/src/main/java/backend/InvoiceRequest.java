package backend;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Payload for creating and updating an invoice.
 *
 * Bean Validation covers required fields, amount ranges and date presence.
 * Business rules that need repository access (client/project references, date
 * ordering, discount limits and the allowed issue state) live in
 * {@link InvoiceService}.
 */
public record InvoiceRequest(

        @NotBlank(message = "Client is required")
        String clientId,

        String projectId,

        @NotNull(message = "Issue date is required")
        LocalDate issueDate,

        @NotNull(message = "Due date is required")
        LocalDate dueDate,

        @Size(max = 30, message = "Status must be at most 30 characters")
        String status,

        @NotEmpty(message = "At least one line item is required")
        @Size(max = 200, message = "An invoice cannot have more than 200 line items")
        List<@Valid InvoiceLineItemRequest> lineItems,

        @DecimalMin(value = "0.00", message = "Tax rate cannot be negative")
        @DecimalMax(value = "100.00", message = "Tax rate cannot exceed 100%")
        BigDecimal taxRate,

        @DecimalMin(value = "0.00", message = "Discount cannot be negative")
        BigDecimal discount,

        @Size(max = 2000, message = "Notes must be at most 2000 characters")
        String notes
) {

    public InvoiceRequest {
        clientId = trim(clientId);
        projectId = trim(projectId);
        status = trim(status);
        notes = trim(notes);
    }

    private static String trim(String value) {
        return value == null ? null : value.trim();
    }
}
