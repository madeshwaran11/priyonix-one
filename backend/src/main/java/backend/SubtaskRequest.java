package backend;

import java.time.LocalDate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Subtask payload used both nested inside {@link TaskRequest} and on the
 * dedicated subtask endpoints to add/update a single subtask.
 */
public record SubtaskRequest(

        @Size(max = 64, message = "Subtask id must be at most 64 characters")
        String id,

        @NotBlank(message = "Subtask title is required")
        @Size(max = 160, message = "Subtask title must be at most 160 characters")
        String title,

        @Size(max = 1000, message = "Subtask description must be at most 1000 characters")
        String description,

        String assigneeId,

        LocalDate dueDate,

        Boolean completed
) {

    public SubtaskRequest {
        id = trim(id);
        title = trim(title);
        description = trim(description);
        assigneeId = trim(assigneeId);
    }

    private static String trim(String value) {
        return value == null ? null : value.trim();
    }
}
