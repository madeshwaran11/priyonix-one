package backend;

import java.time.LocalDate;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * Payload for creating and updating a task.
 *
 * createdBy is intentionally NOT part of this payload: the server always
 * takes it from the authenticated user. Business rules that need repository
 * access (valid project/assignee references, unique task code, enum values,
 * date ranges, status/progress consistency) live in {@link TaskService}.
 */
public record TaskRequest(

        @Size(max = 40, message = "Task code must be at most 40 characters")
        String taskCode,

        @NotBlank(message = "Task title is required")
        @Size(max = 200, message = "Task title must be at most 200 characters")
        String title,

        @Size(max = 2000, message = "Description must be at most 2000 characters")
        String description,

        @NotBlank(message = "Project is required")
        String projectId,

        String assigneeId,

        @NotNull(message = "Start date is required")
        LocalDate startDate,

        @NotNull(message = "Due date is required")
        LocalDate dueDate,

        @Size(max = 20, message = "Priority is invalid")
        String priority,

        @Size(max = 20, message = "Status is invalid")
        String status,

        @Min(value = 0, message = "Progress must be between 0 and 100")
        @Max(value = 100, message = "Progress must be between 0 and 100")
        Integer progress,

        @PositiveOrZero(message = "Estimated hours cannot be negative")
        Double estimatedHours,

        @PositiveOrZero(message = "Actual hours cannot be negative")
        Double actualHours,

        List<@Valid SubtaskRequest> subtasks
) {

    public TaskRequest {
        taskCode = trim(taskCode);
        title = trim(title);
        description = trim(description);
        projectId = trim(projectId);
        assigneeId = trim(assigneeId);
        priority = trim(priority);
        status = trim(status);
    }

    private static String trim(String value) {
        return value == null ? null : value.trim();
    }
}
