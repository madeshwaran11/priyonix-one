package backend;

import java.time.LocalDate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Milestone payload nested inside {@link ProjectRequest}.
 * An existing milestone id is preserved; a new one is generated.
 */
public record MilestoneRequest(

        @Size(max = 64, message = "Milestone id must be at most 64 characters")
        String id,

        @NotBlank(message = "Milestone title is required")
        @Size(max = 160, message = "Milestone title must be at most 160 characters")
        String title,

        @Size(max = 500, message = "Milestone description must be at most 500 characters")
        String description,

        LocalDate dueDate,

        Boolean completed
) {

    public MilestoneRequest {
        id = trim(id);
        title = trim(title);
        description = trim(description);
    }

    private static String trim(String value) {
        return value == null ? null : value.trim();
    }
}
