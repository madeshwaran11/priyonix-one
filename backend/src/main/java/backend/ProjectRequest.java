package backend;

import java.time.LocalDate;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Payload for creating and updating a project.
 *
 * Bean Validation covers required fields, lengths and nested milestones.
 * Business rules that need repository access (duplicate project code, valid
 * manager/team employee references, enum values, date ranges) live in
 * {@link ProjectService}.
 */
public record ProjectRequest(

        @NotBlank(message = "Project code is required")
        @Size(max = 40, message = "Project code must be at most 40 characters")
        String projectCode,

        @NotBlank(message = "Project name is required")
        @Size(max = 160, message = "Project name must be at most 160 characters")
        String name,

        @Size(max = 2000, message = "Description must be at most 2000 characters")
        String description,

        @Size(max = 160, message = "Client name must be at most 160 characters")
        String clientName,

        @NotBlank(message = "Project manager is required")
        String managerId,

        List<String> teamMemberIds,

        @NotNull(message = "Start date is required")
        LocalDate startDate,

        @NotNull(message = "Due date is required")
        LocalDate dueDate,

        @Size(max = 20, message = "Priority is invalid")
        String priority,

        @Size(max = 20, message = "Status is invalid")
        String status,

        List<@Valid MilestoneRequest> milestones
) {
    public ProjectRequest {
        projectCode = trim(projectCode);
        name = trim(name);
        description = trim(description);
        clientName = trim(clientName);
        managerId = trim(managerId);
        priority = trim(priority);
        status = trim(status);
    }

    private static String trim(String value) {
        return value == null ? null : value.trim();
    }
}
