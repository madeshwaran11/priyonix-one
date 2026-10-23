package backend;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Project representation returned by the API.
 *
 * Includes both the stable employee ids (for round-tripping edit forms) and
 * resolved {@link EmployeeRef} objects for display.
 */
public record ProjectResponse(
        String id,
        String projectCode,
        String name,
        String description,
        String clientName,
        String managerId,
        EmployeeRef manager,
        List<String> teamMemberIds,
        List<EmployeeRef> teamMembers,
        LocalDate startDate,
        LocalDate dueDate,
        String priority,
        String status,
        List<Milestone> milestones,
        Instant createdAt,
        Instant updatedAt) {
}
