package backend;

import java.time.LocalDate;
import java.util.List;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Payload for creating and updating an employee.
 *
 * Bean Validation enforces required fields and formats. Business rules that
 * need repository access (duplicate employee ID / work email, allowed
 * employment status) are enforced in {@link EmployeeService}.
 */
public record EmployeeRequest(

        @NotBlank(message = "Employee ID is required")
        @Size(max = 40, message = "Employee ID must be at most 40 characters")
        String employeeId,

        @NotBlank(message = "Full name is required")
        @Size(max = 120, message = "Full name must be at most 120 characters")
        String fullName,

        @NotBlank(message = "Work email is required")
        @Email(message = "Work email must be a valid email address")
        @Size(max = 254, message = "Work email must be at most 254 characters")
        String workEmail,

        @Pattern(
                regexp = "^[0-9+()\\-\\s]{0,30}$",
                message = "Phone may only contain digits, spaces and + ( ) -")
        String phone,

        @NotBlank(message = "Department is required")
        @Size(max = 80, message = "Department must be at most 80 characters")
        String department,

        @NotBlank(message = "Designation is required")
        @Size(max = 80, message = "Designation must be at most 80 characters")
        String designation,

        @NotNull(message = "Joining date is required")
        @PastOrPresent(message = "Joining date cannot be in the future")
        LocalDate joiningDate,

        List<@Size(max = 60, message = "Each skill must be at most 60 characters") String> skills,

        @Size(max = 120, message = "Reporting manager must be at most 120 characters")
        String reportingManager,

        @Size(max = 30, message = "Employment status must be at most 30 characters")
        String employmentStatus
) {

    /**
     * Normalizes whitespace before bean validation runs, so a value such as
     * " Asha@ACME.com " is validated and stored as "Asha@ACME.com" (the
     * service then lower-cases it). Keeps email handling consistent no
     * matter how a client pads its input.
     */
    public EmployeeRequest {
        employeeId = trim(employeeId);
        fullName = trim(fullName);
        workEmail = trim(workEmail);
        phone = trim(phone);
        department = trim(department);
        designation = trim(designation);
        reportingManager = trim(reportingManager);
        employmentStatus = trim(employmentStatus);
    }

    private static String trim(String value) {
        return value == null ? null : value.trim();
    }
}
