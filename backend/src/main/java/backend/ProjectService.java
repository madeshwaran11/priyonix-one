package backend;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

/**
 * Business rules for projects.
 *
 * Responsibilities:
 *  - reject duplicate project codes (case-insensitive);
 *  - validate that the project manager and every team member reference an
 *    existing employee record — never trusting free-text names;
 *  - enforce the allowed priority/status values and valid date ranges;
 *  - resolve employee display names at read time;
 *  - protect projects that still contain tasks from accidental deletion.
 */
@Service
public class ProjectService {
    @org.springframework.beans.factory.annotation.Autowired
    private LegacyIntegrityService integrity;


    public static final Set<String> ALLOWED_PRIORITIES =
            Set.of("LOW", "MEDIUM", "HIGH", "CRITICAL");

    public static final Set<String> ALLOWED_STATUSES =
            Set.of("PLANNING", "ACTIVE", "ON_HOLD", "COMPLETED", "CANCELLED");

    public static final String DEFAULT_PRIORITY = "MEDIUM";
    public static final String DEFAULT_STATUS = "PLANNING";

    private final ProjectRepository projectRepository;
    private final EmployeeRepository employeeRepository;
    private final TaskRepository taskRepository;

    public ProjectService(
            ProjectRepository projectRepository,
            EmployeeRepository employeeRepository,
            TaskRepository taskRepository) {
        this.projectRepository = projectRepository;
        this.employeeRepository = employeeRepository;
        this.taskRepository = taskRepository;
    }

    // ----- queries ------------------------------------------------------

    public PagedResult<ProjectResponse> list(
            String search,
            String status,
            String priority,
            int page,
            int size) {

        String statusFilter = status == null || status.isBlank()
                ? null : requireEnum(status, ALLOWED_STATUSES, "Status");
        String priorityFilter = priority == null || priority.isBlank()
                ? null : requireEnum(priority, ALLOWED_PRIORITIES, "Priority");
        String needle = search == null ? null
                : search.trim().toLowerCase(Locale.ROOT);

        List<Project> filtered = projectRepository
                .findAllByOrderByCreatedAtDesc()
                .stream()
                .filter(project -> statusFilter == null
                        || statusFilter.equalsIgnoreCase(project.getStatus()))
                .filter(project -> priorityFilter == null
                        || priorityFilter.equalsIgnoreCase(project.getPriority()))
                .filter(project -> needle == null || needle.isEmpty()
                        || matches(project, needle))
                .toList();

        Map<String, Employee> employees =
                resolveEmployees(collectEmployeeIds(filtered));

        List<ProjectResponse> responses = filtered.stream()
                .map(project -> toResponse(project, employees))
                .toList();

        return PagedResult.of(responses, page, size);
    }

    public Project getEntity(String id) {
        return projectRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Project not found: " + id));
    }

    public ProjectResponse getById(String id) {
        Project project = getEntity(id);
        Map<String, Employee> employees =
                resolveEmployees(collectEmployeeIds(List.of(project)));
        return toResponse(project, employees);
    }

    public ProjectSummaryResponse summary(String id) {
        Project project = getEntity(id);
        List<Task> tasks = taskRepository.findByProjectId(id);

        LocalDate today = LocalDate.now();

        long total = tasks.size();
        long completed = tasks.stream()
                .filter(task -> "COMPLETED".equals(task.getStatus()))
                .count();
        long inProgress = tasks.stream()
                .filter(task -> "IN_PROGRESS".equals(task.getStatus()))
                .count();
        long overdue = tasks.stream()
                .filter(task -> isOverdue(task, today))
                .count();

        int progressPercent = total == 0 ? 0 : (int) Math.round(
                tasks.stream().mapToInt(Task::getProgress).average().orElse(0));

        int totalMilestones = project.getMilestones().size();
        int completedMilestones = (int) project.getMilestones().stream()
                .filter(Milestone::isCompleted)
                .count();
        int milestoneProgress = totalMilestones == 0 ? 0
                : (int) Math.round(
                        completedMilestones * 100.0 / totalMilestones);

        return new ProjectSummaryResponse(
                project.getId(),
                project.getProjectCode(),
                project.getName(),
                project.getStatus(),
                project.getPriority(),
                project.getStartDate(),
                project.getDueDate(),
                total,
                completed,
                inProgress,
                overdue,
                progressPercent,
                totalMilestones,
                completedMilestones,
                milestoneProgress);
    }

    // ----- mutations ----------------------------------------------------

    public ProjectResponse create(ProjectRequest request) {
        String code = request.projectCode();

        if (projectRepository.existsByProjectCodeIgnoreCase(code)) {
            throw new DuplicateResourceException(
                    "A project with code " + code + " already exists");
        }

        String priority = normalizeEnum(
                request.priority(), ALLOWED_PRIORITIES, DEFAULT_PRIORITY,
                "Priority");
        String status = normalizeEnum(
                request.status(), ALLOWED_STATUSES, DEFAULT_STATUS, "Status");
        validateDates(request.startDate(), request.dueDate());

        String managerId = requireEmployee(request.managerId())
                .getId();
        List<String> teamIds = validateTeam(request.teamMemberIds());
        List<Milestone> milestones = mapMilestones(request.milestones());

        Project project = new Project();
        project.setCreatedAt(Instant.now());
        apply(project, request, code, managerId, teamIds, priority, status,
                milestones);
        project.setUpdatedAt(Instant.now());

        Project saved = projectRepository.save(project);

        Map<String, Employee> employees =
                resolveEmployees(collectEmployeeIds(List.of(saved)));
        return toResponse(saved, employees);
    }

    public ProjectResponse update(String id, ProjectRequest request) {
        Project existing = getEntity(id);
        String code = request.projectCode();

        if (!equalsIgnoreCase(existing.getProjectCode(), code)
                && projectRepository.existsByProjectCodeIgnoreCase(code)) {
            throw new DuplicateResourceException(
                    "A project with code " + code + " already exists");
        }

        String priority = normalizeEnum(
                request.priority(), ALLOWED_PRIORITIES, DEFAULT_PRIORITY,
                "Priority");
        String status = normalizeEnum(
                request.status(), ALLOWED_STATUSES, DEFAULT_STATUS, "Status");
        validateDates(request.startDate(), request.dueDate());

        String managerId = requireEmployee(request.managerId()).getId();
        List<String> teamIds = validateTeam(request.teamMemberIds());
        List<Milestone> milestones = mapMilestones(request.milestones());

        apply(existing, request, code, managerId, teamIds, priority, status,
                milestones);
        // createdAt is preserved; only updatedAt moves forward.
        existing.setUpdatedAt(Instant.now());

        Project saved = projectRepository.save(existing);

        Map<String, Employee> employees =
                resolveEmployees(collectEmployeeIds(List.of(saved)));
        return toResponse(saved, employees);
    }

    /**
     * Deletes a project. A project that still contains tasks is protected
     * unless {@code cascade} is true, in which case its tasks are removed
     * first. Callers must be administrators (enforced in SecurityConfig).
     */
    public void delete(String id, boolean cascade) {
        if (integrity != null) integrity.requireUnreferenced("projects",id);
        Project project = getEntity(id);
        long taskCount = taskRepository.countByProjectId(id);

        if (taskCount > 0 && !cascade) {
            throw new ConflictException(
                    "Project has " + taskCount + " task(s). Delete them or "
                            + "retry with ?cascade=true to remove the project "
                            + "and its tasks");
        }

        if (cascade && taskCount > 0) {
            taskRepository.deleteByProjectId(id);
        }

        projectRepository.delete(project);
    }

    // ----- helpers ------------------------------------------------------

    private void apply(
            Project project,
            ProjectRequest request,
            String code,
            String managerId,
            List<String> teamIds,
            String priority,
            String status,
            List<Milestone> milestones) {

        project.setProjectCode(code);
        project.setName(request.name());
        project.setDescription(blankToNull(request.description()));
        project.setClientName(blankToNull(request.clientName()));
        project.setManagerId(managerId);
        project.setTeamMemberIds(teamIds);
        project.setStartDate(request.startDate());
        project.setDueDate(request.dueDate());
        project.setPriority(priority);
        project.setStatus(status);
        project.setMilestones(milestones);
    }

    private ProjectResponse toResponse(
            Project project, Map<String, Employee> employees) {

        List<EmployeeRef> teamMembers = project.getTeamMemberIds().stream()
                .map(id -> lookup(employees, id))
                .filter(Objects::nonNull)
                .map(EmployeeRef::from)
                .toList();

        return new ProjectResponse(
                project.getId(),
                project.getProjectCode(),
                project.getName(),
                project.getDescription(),
                project.getClientName(),
                project.getManagerId(),
                EmployeeRef.from(lookup(employees, project.getManagerId())),
                project.getTeamMemberIds(),
                teamMembers,
                project.getStartDate(),
                project.getDueDate(),
                project.getPriority(),
                project.getStatus(),
                project.getMilestones(),
                project.getCreatedAt(),
                project.getUpdatedAt());
    }

    private Employee requireEmployee(String employeeId) {
        return employeeRepository.findById(employeeId)
                .orElseThrow(() -> new InvalidRequestException(
                        "Project manager references an unknown employee: "
                                + employeeId));
    }

    private List<String> validateTeam(List<String> teamMemberIds) {
        if (teamMemberIds == null || teamMemberIds.isEmpty()) {
            return new ArrayList<>();
        }

        // Trim, drop blanks and de-duplicate while preserving order.
        List<String> unique = new ArrayList<>(teamMemberIds.stream()
                .filter(id -> id != null && !id.isBlank())
                .map(String::trim)
                .collect(LinkedHashSet::new, LinkedHashSet::add,
                        LinkedHashSet::addAll));

        for (String id : unique) {
            if (employeeRepository.findById(id).isEmpty()) {
                throw new InvalidRequestException(
                        "Team member references an unknown employee: " + id);
            }
        }

        return unique;
    }

    private List<Milestone> mapMilestones(List<MilestoneRequest> requests) {
        if (requests == null) {
            return new ArrayList<>();
        }

        List<Milestone> milestones = new ArrayList<>();
        for (MilestoneRequest request : requests) {
            Milestone milestone = new Milestone();
            milestone.setId(request.id() == null || request.id().isBlank()
                    ? UUID.randomUUID().toString()
                    : request.id());
            milestone.setTitle(request.title());
            milestone.setDescription(blankToNull(request.description()));
            milestone.setDueDate(request.dueDate());
            milestone.setCompleted(Boolean.TRUE.equals(request.completed()));
            milestones.add(milestone);
        }
        return milestones;
    }

    private Map<String, Employee> resolveEmployees(Collection<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return Map.of();
        }
        List<Employee> found = employeeRepository.findAllById(ids);
        return found.stream().collect(Collectors.toMap(
                Employee::getId, Function.identity(), (a, b) -> a));
    }

    private Set<String> collectEmployeeIds(List<Project> projects) {
        Set<String> ids = new LinkedHashSet<>();
        for (Project project : projects) {
            if (project.getManagerId() != null) {
                ids.add(project.getManagerId());
            }
            if (project.getTeamMemberIds() != null) {
                ids.addAll(project.getTeamMemberIds());
            }
        }
        return ids;
    }

    private boolean matches(Project project, String needle) {
        return contains(project.getProjectCode(), needle)
                || contains(project.getName(), needle)
                || contains(project.getClientName(), needle)
                || contains(project.getDescription(), needle);
    }

    private boolean contains(String value, String needle) {
        return value != null
                && value.toLowerCase(Locale.ROOT).contains(needle);
    }

    /**
     * Null-safe map lookup. {@link Map#of()} throws NPE on a null key, so
     * absent references must be guarded.
     */
    private static <T> T lookup(Map<String, T> map, String key) {
        return key == null ? null : map.get(key);
    }

    private static boolean isOverdue(Task task, LocalDate today) {
        return task.getDueDate() != null
                && task.getDueDate().isBefore(today)
                && !"COMPLETED".equals(task.getStatus());
    }

    private static void validateDates(LocalDate startDate, LocalDate dueDate) {
        if (startDate != null && dueDate != null
                && dueDate.isBefore(startDate)) {
            throw new InvalidRequestException(
                    "Due date cannot be before the start date");
        }
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
