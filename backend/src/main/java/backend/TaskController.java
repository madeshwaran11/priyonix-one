package backend;

import java.util.LinkedHashMap;
import java.util.Map;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Authenticated Task and Subtask REST API.
 *
 * Access rules live in {@link SecurityConfig}: every route requires a valid
 * JWT and DELETE additionally requires the ADMIN role. createdBy is always
 * taken from the authenticated principal, never from the request body.
 */
@RestController
@RequestMapping("/api/tasks")
public class TaskController {

    private final TaskService taskService;

    public TaskController(TaskService taskService) {
        this.taskService = taskService;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> list(
            @RequestParam(name = "search", required = false) String search,
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "priority", required = false) String priority,
            @RequestParam(name = "projectId", required = false) String projectId,
            @RequestParam(name = "assigneeId", required = false)
            String assigneeId,
            @RequestParam(name = "overdue", defaultValue = "false")
            boolean overdue,
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "size", defaultValue = "20") int size) {

        PagedResult<TaskResponse> result = taskService.list(
                search, status, priority, projectId, assigneeId, overdue,
                page, size);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "SUCCESS");
        body.put("count", result.items().size());
        body.put("total", result.total());
        body.put("page", result.page());
        body.put("size", result.size());
        body.put("totalPages", result.totalPages());
        body.put("tasks", result.items());

        return ResponseEntity.ok(body);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getById(
            @PathVariable String id) {

        return ResponseEntity.ok(Map.of(
                "status", "SUCCESS",
                "task", taskService.getById(id)));
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> create(
            @Valid @RequestBody TaskRequest request,
            Authentication authentication) {

        TaskResponse created =
                taskService.create(request, authentication.getName());

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "status", "SUCCESS",
                "message", "Task created",
                "task", created));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Map<String, Object>> update(
            @PathVariable String id,
            @Valid @RequestBody TaskRequest request) {

        return ResponseEntity.ok(Map.of(
                "status", "SUCCESS",
                "message", "Task updated",
                "task", taskService.update(id, request)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> delete(
            @PathVariable String id) {

        taskService.delete(id);

        return ResponseEntity.ok(Map.of(
                "status", "SUCCESS",
                "message", "Task deleted"));
    }

    // ----- subtasks -----------------------------------------------------

    @PostMapping("/{taskId}/subtasks")
    public ResponseEntity<Map<String, Object>> addSubtask(
            @PathVariable String taskId,
            @Valid @RequestBody SubtaskRequest request) {

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "status", "SUCCESS",
                "message", "Subtask created",
                "task", taskService.addSubtask(taskId, request)));
    }

    @PutMapping("/{taskId}/subtasks/{subtaskId}")
    public ResponseEntity<Map<String, Object>> updateSubtask(
            @PathVariable String taskId,
            @PathVariable String subtaskId,
            @Valid @RequestBody SubtaskRequest request) {

        return ResponseEntity.ok(Map.of(
                "status", "SUCCESS",
                "message", "Subtask updated",
                "task", taskService.updateSubtask(taskId, subtaskId, request)));
    }

    @DeleteMapping("/{taskId}/subtasks/{subtaskId}")
    public ResponseEntity<Map<String, Object>> deleteSubtask(
            @PathVariable String taskId,
            @PathVariable String subtaskId) {

        return ResponseEntity.ok(Map.of(
                "status", "SUCCESS",
                "message", "Subtask deleted",
                "task", taskService.deleteSubtask(taskId, subtaskId)));
    }
}
