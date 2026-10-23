package backend;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Follow-up representation returned by the API.
 *
 * Includes the raw lead/client ids (for edit forms) plus resolved display
 * names and a server-computed overdue flag so the frontend never has to guess.
 */
public record FollowUpResponse(
        String id,
        String activityType,
        LocalDate dueDate,
        String notes,
        boolean completed,
        String assignedToId,
        EmployeeRef assignedTo,
        String leadId,
        String leadName,
        String clientId,
        String clientName,
        boolean overdue,
        Instant createdAt,
        Instant updatedAt) {
}
