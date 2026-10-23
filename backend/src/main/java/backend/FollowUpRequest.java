package backend;

import java.time.LocalDate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Payload for creating and updating a follow-up activity.
 *
 * A follow-up must reference exactly one lead OR one client; that cross-field
 * rule needs repository access and is enforced in {@link FollowUpService}.
 */
public record FollowUpRequest(

        @NotBlank(message = "Activity type is required")
        @Size(max = 30, message = "Activity type must be at most 30 characters")
        String activityType,

        @NotNull(message = "Due date is required")
        LocalDate dueDate,

        @Size(max = 2000, message = "Notes must be at most 2000 characters")
        String notes,

        Boolean completed,

        String assignedToId,

        String leadId,

        String clientId
) {

    public FollowUpRequest {
        activityType = trim(activityType);
        notes = trim(notes);
        assignedToId = trim(assignedToId);
        leadId = trim(leadId);
        clientId = trim(clientId);
    }

    private static String trim(String value) {
        return value == null ? null : value.trim();
    }
}
