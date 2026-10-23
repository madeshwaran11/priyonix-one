package backend;

import java.time.LocalDate;

/**
 * Progress summary for a single project, returned by
 * {@code GET /api/projects/{id}/summary}.
 *
 * progressPercent is the average progress of the project's tasks (0 when the
 * project has no tasks). overdueTasks counts tasks past their due date that
 * are not yet COMPLETED.
 */
public record ProjectSummaryResponse(
        String projectId,
        String projectCode,
        String name,
        String status,
        String priority,
        LocalDate startDate,
        LocalDate dueDate,
        long totalTasks,
        long completedTasks,
        long inProgressTasks,
        long overdueTasks,
        int progressPercent,
        int totalMilestones,
        int completedMilestones,
        int milestoneProgressPercent) {
}
