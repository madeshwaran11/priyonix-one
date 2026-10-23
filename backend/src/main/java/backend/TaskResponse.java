package backend;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Task representation returned by the API.
 *
 * Includes the raw project/assignee ids (for edit forms) plus resolved
 * display references, subtask counts and a server-computed overdue flag so
 * the frontend never has to guess.
 */
public record TaskResponse(
        String id,
        String taskCode,
        String title,
        String description,
        String projectId,
        String projectCode,
        String projectName,
        String assigneeId,
        EmployeeRef assignee,
        String createdBy,
        LocalDate startDate,
        LocalDate dueDate,
        String priority,
        String status,
        int progress,
        Double estimatedHours,
        Double actualHours,
        List<Subtask> subtasks,
        int subtaskCount,
        int completedSubtaskCount,
        boolean overdue,
        Instant createdAt,
        Instant updatedAt) {
}
