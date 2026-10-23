package backend;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * Payload for creating and updating a lead.
 *
 * Bean Validation enforces required fields, the email format and lengths.
 * Business rules that need repository access (valid assigned employee
 * reference, allowed status/priority) live in {@link LeadService}.
 */
public record LeadRequest(

        @NotBlank(message = "Lead name is required")
        @Size(max = 160, message = "Lead name must be at most 160 characters")
        String name,

        @Size(max = 160, message = "Company must be at most 160 characters")
        String company,

        @NotBlank(message = "Email is required")
        @Email(message = "Email must be a valid email address")
        @Size(max = 254, message = "Email must be at most 254 characters")
        String email,

        @Pattern(
                regexp = "^[0-9+()\\-\\s]{0,30}$",
                message = "Phone may only contain digits, spaces and + ( ) -")
        String phone,

        @Size(max = 80, message = "Source must be at most 80 characters")
        String source,

        @Size(max = 30, message = "Status must be at most 30 characters")
        String status,

        @Size(max = 20, message = "Priority must be at most 20 characters")
        String priority,

        String assignedToId,

        @PositiveOrZero(message = "Estimated value cannot be negative")
        BigDecimal estimatedValue,

        LocalDate followUpDate,

        @Size(max = 2000, message = "Notes must be at most 2000 characters")
        String notes,

        String campaignId
) {

    public LeadRequest {
        name = trim(name);
        company = trim(company);
        email = trim(email);
        phone = trim(phone);
        source = trim(source);
        status = trim(status);
        priority = trim(priority);
        assignedToId = trim(assignedToId);
        notes = trim(notes);
        campaignId = trim(campaignId);
    }

    private static String trim(String value) {
        return value == null ? null : value.trim();
    }
}
