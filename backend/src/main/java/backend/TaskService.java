package backend;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;

/**
 * Business rules for tasks and their subtasks.
 *
 * Responsibilities:
 *  - reject duplicate task codes; auto-generate one when omitted;
 *  - validate that the project and assignee references exist;
 *  - enforce allowed priority/status values, valid date ranges and a
 *    0-100 progress range;
 *  - keep status and progress consistent (TODO = 0%, COMPLETED = 100%,
 *    100% becomes COMPLETED);
 *  - take createdBy from the authenticated user, never from the client.
 */
@Service
public class TaskService {

    public static final Set<String> ALLOWED_PRIORITIES =
            Set.of("LOW", "MEDIUM", "HIGH", "CRITICAL");

    public static final Set<String> ALLOWED_STATUSES =
            Set.of("TODO", "IN_PROGRESS", "IN_REVIEW", "BLOCKED", "COMPLETED");

    public static final String DEFAULT_PRIORITY = "MEDIUM";
    public static final String DEFAULT_STATUS = "TODO";
    public static final String COMPLETED = "COMPLETED";

    private final TaskRepository taskRepository;
    private final ProjectRepository projectRepository;
    private final EmployeeRepository employeeRepository;

    public TaskService(
            TaskRepository taskRepository,
            ProjectRepository projectRepository,
            EmployeeRepository employeeRepository) {
        this.taskRepository = taskRepository;
        this.projectRepository = projectRepository;
        this.employeeRepository = employeeRepository;
    }

    // ----- queries ------------------------------------------------------

    public PagedResult<TaskResponse> list(
            String search,
            String status,
            String priority,
            String projectId,
            String assigneeId,
            boolean overdueOnly,
            int page,
            int size) {

        String statusFilter = status == null || status.isBlank()
                ? null : requireEnum(status, ALLOWED_STATUSES, "Status");
        String priorityFilter = priority == null || priority.isBlank()
                ? null : requireEnum(priority, ALLOWED_PRIORITIES, "Priority");
        String needle = search == null ? null
                : search.trim().toLowerCase(Locale.ROOT);
        LocalDate today = LocalDate.now();

        List<Task> filtered = taskRepository.findAllByOrderByCreatedAtDesc()
                .stream()
                .filter(task -> statusFilter == null
                        || statusFilter.equalsIgnoreCase(task.getStatus()))
                .filter(task -> priorityFilter == null
                        || priorityFilter.equalsIgnoreCase(task.getPriority()))
                .filter(task -> projectId == null || projectId.isBlank()
                        || projectId.equals(task.getProjectId()))
                .filter(task -> assigneeId == null || assigneeId.isBlank()
                        || assigneeId.equals(task.getAssigneeId()))
                .filter(task -> !overdueOnly || isOverdue(task, today))
                .filter(task -> needle == null || needle.isEmpty()
                        || matches(task, needle))
                .toList();

        List<TaskResponse> responses = toResponses(filtered);

        return PagedResult.of(responses, page, size);
    }

    public List<TaskResponse> listByProject(String projectId) {
        if (projectRepository.findById(projectId).isEmpty()) {
            throw new ResourceNotFoundException(
                    "Project not found: " + projectId);
        }
        return toResponses(taskRepository.findByProjectId(projectId));
    }

    public Task getEntity(String id) {
        return taskRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Task not found: " + id));
    }

    public TaskResponse getById(String id) {
        return toResponses(List.of(getEntity(id))).get(0);
    }

    // ----- mutations ----------------------------------------------------

    public TaskResponse create(TaskRequest request, String createdBy) {
        String code = request.taskCode() == null || request.taskCode().isBlank()
                ? generateTaskCode()
                : request.taskCode();

        if (taskRepository.existsByTaskCodeIgnoreCase(code)) {
            throw new DuplicateResourceException(
                    "A task with code " + code + " already exists");
        }

        Validated validated = validate(request);

        Task task = new Task();
        task.setTaskCode(code);
        task.setCreatedBy(createdBy);
        task.setCreatedAt(Instant.now());
        apply(task, request, validated);
        task.setUpdatedAt(Instant.now());

        Task saved = taskRepository.save(task);
        return toResponses(List.of(saved)).get(0);
    }

    public TaskResponse update(String id, TaskRequest request) {
        Task existing = getEntity(id);

        String code = request.taskCode() == null || request.taskCode().isBlank()
                ? existing.getTaskCode()
                : request.taskCode();

        if (!equalsIgnoreCase(existing.getTaskCode(), code)
                && taskRepository.existsByTaskCodeIgnoreCase(code)) {
            throw new DuplicateResourceException(
                    "A task with code " + code + " already exists");
        }

        Validated validated = validate(request);

        existing.setTaskCode(code);
        apply(existing, request, validated);
        // createdBy and createdAt are preserved.
        existing.setUpdatedAt(Instant.now());

        Task saved = taskRepository.save(existing);
        return toResponses(List.of(saved)).get(0);
    }

    public void delete(String id) {
        Task task = getEntity(id);
        taskRepository.delete(task);
    }

    // ----- subtasks -----------------------------------------------------

    public TaskResponse addSubtask(String taskId, SubtaskRequest request) {
        Task task = getEntity(taskId);
        task.getSubtasks().add(mapSubtask(request, null));
        task.setUpdatedAt(Instant.now());
        return toResponses(List.of(taskRepository.save(task))).get(0);
    }

    public TaskResponse updateSubtask(
            String taskId, String subtaskId, SubtaskRequest request) {

        Task task = getEntity(taskId);
        Subtask subtask = task.getSubtasks().stream()
                .filter(item -> subtaskId.equals(item.getId()))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Subtask not found: " + subtaskId));

        Subtask updated = mapSubtask(request, subtask.getId());
        updated.setCreatedAt(subtask.getCreatedAt());

        List<Subtask> subtasks = new ArrayList<>(task.getSubtasks());
        subtasks.replaceAll(item -> subtaskId.equals(item.getId())
                ? updated : item);
        task.setSubtasks(subtasks);
        task.setUpdatedAt(Instant.now());

        return toResponses(List.of(taskRepository.save(task))).get(0);
    }

    public TaskResponse deleteSubtask(String taskId, String subtaskId) {
        Task task = getEntity(taskId);

        boolean removed = task.getSubtasks()
                .removeIf(item -> subtaskId.equals(item.getId()));

        if (!removed) {
            throw new ResourceNotFoundException(
                    "Subtask not found: " + subtaskId);
        }

        task.setUpdatedAt(Instant.now());
        return toResponses(List.of(taskRepository.save(task))).get(0);
    }

    // ----- helpers ------------------------------------------------------

    private Validated validate(TaskRequest request) {
        if (projectRepository.findById(request.projectId()).isEmpty()) {
            throw new InvalidRequestException(
                    "Task references an unknown project: "
                            + request.projectId());
        }

        String assigneeId = blankToNull(request.assigneeId());
        if (assigneeId != null
                && employeeRepository.findById(assigneeId).isEmpty()) {
            throw new InvalidRequestException(
                    "Task assignee references an unknown employee: "
                            + assigneeId);
        }

        String priority = normalizeEnum(
                request.priority(), ALLOWED_PRIORITIES, DEFAULT_PRIORITY,
                "Priority");
        String status = normalizeEnum(
                request.status(), ALLOWED_STATUSES, DEFAULT_STATUS, "Status");
        int progress = request.progress() == null ? 0 : clamp(request.progress());

        if (request.startDate() != null && request.dueDate() != null
                && request.dueDate().isBefore(request.startDate())) {
            throw new InvalidRequestException(
                    "Due date cannot be before the start date");
        }

        // Keep status and progress consistent:
        //  - TODO always means 0%;
        //  - COMPLETED always means 100%;
        //  - 100% always means COMPLETED.
        if ("TODO".equals(status)) {
            progress = 0;
        } else if (COMPLETED.equals(status)) {
            progress = 100;
        } else if (progress == 100) {
            status = COMPLETED;
        }

        return new Validated(assigneeId, priority, status, progress);
    }

    private void apply(Task task, TaskRequest request, Validated validated) {
        task.setTitle(request.title());
        task.setDescription(blankToNull(request.description()));
        task.setProjectId(request.projectId());
        task.setAssigneeId(validated.assigneeId());
        task.setStartDate(request.startDate());
        task.setDueDate(request.dueDate());
        task.setPriority(validated.priority());
        task.setStatus(validated.status());
        task.setProgress(validated.progress());
        task.setEstimatedHours(request.estimatedHours());
        task.setActualHours(request.actualHours());

        if (request.subtasks() != null) {
            task.setSubtasks(mapSubtasks(request.subtasks()));
        }
    }

    /**
     * Keeps status and progress consistent:
     *  - TODO always means 0%;
     *  - COMPLETED always means 100%;
     *  - 100% always means COMPLETED.
     * Other statuses keep the supplied progress (clamped to 0-100).
     */
    private record Validated(
            String assigneeId, String priority, String status, int progress) {
    }

    private List<Subtask> mapSubtasks(List<SubtaskRequest> requests) {
        List<Subtask> subtasks = new ArrayList<>();
        for (SubtaskRequest request : requests) {
            subtasks.add(mapSubtask(request, null));
        }
        return subtasks;
    }

    private Subtask mapSubtask(SubtaskRequest request, String existingId) {
        String assigneeId = blankToNull(request.assigneeId());
        if (assigneeId != null
                && employeeRepository.findById(assigneeId).isEmpty()) {
            throw new InvalidRequestException(
                    "Subtask assignee references an unknown employee: "
                            + assigneeId);
        }

        Subtask subtask = new Subtask();
        subtask.setId(existingId != null
                ? existingId
                : (request.id() == null || request.id().isBlank()
                        ? UUID.randomUUID().toString()
                        : request.id()));
        subtask.setTitle(request.title());
        subtask.setDescription(blankToNull(request.description()));
        subtask.setAssigneeId(assigneeId);
        subtask.setDueDate(request.dueDate());
        subtask.setCompleted(Boolean.TRUE.equals(request.completed()));
        subtask.setCreatedAt(Instant.now());
        return subtask;
    }

    private List<TaskResponse> toResponses(List<Task> tasks) {
        Map<String, Project> projects = resolveProjects(tasks);
        Map<String, Employee> employees = resolveEmployees(tasks);
        LocalDate today = LocalDate.now();

        return tasks.stream()
                .map(task -> toResponse(task, projects, employees, today))
                .toList();
    }

    private TaskResponse toResponse(
            Task task,
            Map<String, Project> projects,
            Map<String, Employee> employees,
            LocalDate today) {

        Project project = lookup(projects, task.getProjectId());
        int subtaskCount = task.getSubtasks().size();
        int completedSubtasks = (int) task.getSubtasks().stream()
                .filter(Subtask::isCompleted)
                .count();

        return new TaskResponse(
                task.getId(),
                task.getTaskCode(),
                task.getTitle(),
                task.getDescription(),
                task.getProjectId(),
                project == null ? null : project.getProjectCode(),
                project == null ? null : project.getName(),
                task.getAssigneeId(),
                EmployeeRef.from(lookup(employees, task.getAssigneeId())),
                task.getCreatedBy(),
                task.getStartDate(),
                task.getDueDate(),
                task.getPriority(),
                task.getStatus(),
                task.getProgress(),
                task.getEstimatedHours(),
                task.getActualHours(),
                task.getSubtasks(),
                subtaskCount,
                completedSubtasks,
                isOverdue(task, today),
                task.getCreatedAt(),
                task.getUpdatedAt());
    }

    private Map<String, Project> resolveProjects(List<Task> tasks) {
        Set<String> ids = new LinkedHashSet<>();
        for (Task task : tasks) {
            if (task.getProjectId() != null) {
                ids.add(task.getProjectId());
            }
        }
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<String, Project> map = new HashMap<>();
        for (Project project : projectRepository.findAllById(ids)) {
            map.put(project.getId(), project);
        }
        return map;
    }

    private Map<String, Employee> resolveEmployees(List<Task> tasks) {
        Set<String> ids = new LinkedHashSet<>();
        for (Task task : tasks) {
            if (task.getAssigneeId() != null) {
                ids.add(task.getAssigneeId());
            }
            for (Subtask subtask : task.getSubtasks()) {
                if (subtask.getAssigneeId() != null) {
                    ids.add(subtask.getAssigneeId());
                }
            }
        }
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<String, Employee> map = new HashMap<>();
        for (Employee employee : employeeRepository.findAllById(ids)) {
            map.put(employee.getId(), employee);
        }
        return map;
    }

    private String generateTaskCode() {
        for (int attempt = 0; attempt < 5; attempt++) {
            String candidate = "TASK-"
                    + UUID.randomUUID().toString()
                            .replace("-", "")
                            .substring(0, 8)
                            .toUpperCase(Locale.ROOT);
            if (!taskRepository.existsByTaskCodeIgnoreCase(candidate)) {
                return candidate;
            }
        }
        // Extremely unlikely fallback.
        return "TASK-" + UUID.randomUUID().toString()
                .replace("-", "").toUpperCase(Locale.ROOT);
    }

    private boolean matches(Task task, String needle) {
        return contains(task.getTitle(), needle)
                || contains(task.getTaskCode(), needle)
                || contains(task.getDescription(), needle)
                || contains(task.getCreatedBy(), needle);
    }

    private boolean contains(String value, String needle) {
        return value != null
                && value.toLowerCase(Locale.ROOT).contains(needle);
    }

    /**
     * Null-safe map lookup. {@link Map#of()} throws NPE on a null key, so
     * absent references (for example an unassigned task) must be guarded.
     */
    private static <T> T lookup(Map<String, T> map, String key) {
        return key == null ? null : map.get(key);
    }

    private static boolean isOverdue(Task task, LocalDate today) {
        return task.getDueDate() != null
                && task.getDueDate().isBefore(today)
                && !COMPLETED.equals(task.getStatus());
    }

    private static int clamp(int progress) {
        return Math.max(0, Math.min(100, progress));
    }

    private static String normalizeEnum(
            String value, Set<String> allowed, String defaultValue,
            String fieldName) {

        if (value == null || value.isBlank()) {
            return defaultValue;
        }

        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (!allowed.contains(normalized)) {
            throw new InvalidRequestException(
                    fieldName + " must be one of "
                            + String.join(", ", allowed));
        }
        return normalized;
    }

    private static String requireEnum(
            String value, Set<String> allowed, String fieldName) {
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (!allowed.contains(normalized)) {
            throw new InvalidRequestException(
                    fieldName + " must be one of "
                            + String.join(", ", allowed));
        }
        return normalized;
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private static boolean equalsIgnoreCase(String left, String right) {
        return left != null && right != null && left.equalsIgnoreCase(right);
    }
}
