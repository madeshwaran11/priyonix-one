package backend;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Payload for creating and updating a client.
 *
 * Bean Validation enforces required fields, the email format and lengths.
 * Business rules that need repository access (duplicate email, valid assigned
 * employee reference, allowed status) live in {@link ClientService}.
 */
public record ClientRequest(

        @NotBlank(message = "Client name is required")
        @Size(max = 160, message = "Client name must be at most 160 characters")
        String name,

        @Size(max = 160, message = "Company name must be at most 160 characters")
        String companyName,

        @NotBlank(message = "Email is required")
        @Email(message = "Email must be a valid email address")
        @Size(max = 254, message = "Email must be at most 254 characters")
        String email,

        @Pattern(
                regexp = "^[0-9+()\\-\\s]{0,30}$",
                message = "Phone may only contain digits, spaces and + ( ) -")
        String phone,

        @Size(max = 400, message = "Address must be at most 400 characters")
        String address,

        @Size(max = 120, message = "Industry must be at most 120 characters")
        String industry,

        @Size(max = 30, message = "Status must be at most 30 characters")
        String status,

        String assignedToId,

        @Size(max = 2000, message = "Notes must be at most 2000 characters")
        String notes
) {

    public ClientRequest {
        name = trim(name);
        companyName = trim(companyName);
        email = trim(email);
        phone = trim(phone);
        address = trim(address);
        industry = trim(industry);
        status = trim(status);
        assignedToId = trim(assignedToId);
        notes = trim(notes);
    }

    private static String trim(String value) {
        return value == null ? null : value.trim();
    }
}
