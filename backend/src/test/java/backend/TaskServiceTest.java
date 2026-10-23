package backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TaskServiceTest {

    @Mock
    private TaskRepository taskRepository;

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private EmployeeRepository employeeRepository;

    private TaskService taskService;

    @BeforeEach
    void setUp() {
        taskService = new TaskService(
                taskRepository, projectRepository, employeeRepository);
    }

    private Project project(String id, String code) {
        Project project = new Project();
        project.setId(id);
        project.setProjectCode(code);
        project.setName("Website Redesign");
        return project;
    }

    private Employee employee(String id, String employeeId, String name) {
        Employee employee = new Employee();
        employee.setId(id);
        employee.setEmployeeId(employeeId);
        employee.setFullName(name);
        return employee;
    }

    private TaskRequest request(
            String code, String projectId, String assigneeId,
            LocalDate start, LocalDate due, String status, Integer progress) {
        return new TaskRequest(
                code, "Build login page", "Implement the auth screen",
                projectId, assigneeId, start, due, "HIGH", status, progress,
                null, null, null);
    }

    private void stubCreateHappyPath(String code) {
        when(taskRepository.existsByTaskCodeIgnoreCase(code)).thenReturn(false);
    }

    private Task storedTask() {
        Task task = new Task();
        task.setId("task-doc-1");
        task.setTaskCode("TASK-001");
        task.setTitle("Build login page");
        task.setProjectId("prj-1");
        task.setAssigneeId("emp-1");
        task.setCreatedBy("admin@example.com");
        task.setStartDate(LocalDate.of(2024, 1, 1));
        task.setDueDate(LocalDate.of(2024, 2, 1));
        task.setPriority("HIGH");
        task.setStatus("IN_PROGRESS");
        task.setProgress(40);
        task.setSubtasks(new ArrayList<>());
        task.setCreatedAt(Instant.parse("2024-01-01T00:00:00Z"));
        return task;
    }

    // ----- create validation --------------------------------------------

    @Test
    void createRejectsUnknownProject() {
        stubCreateHappyPath("TASK-001");
        when(projectRepository.findById("ghost")).thenReturn(Optional.empty());

        assertThrows(InvalidRequestException.class,
                () -> taskService.create(request("TASK-001", "ghost", null,
                        LocalDate.of(2024, 1, 1), LocalDate.of(2024, 2, 1),
                        "TODO", 0), "admin@example.com"));

        verify(taskRepository, never()).save(any(Task.class));
    }

    @Test
    void createRejectsUnknownAssignee() {
        stubCreateHappyPath("TASK-001");
        when(projectRepository.findById("prj-1"))
                .thenReturn(Optional.of(project("prj-1", "PRJ-001")));
        when(employeeRepository.findById("ghost")).thenReturn(Optional.empty());

        assertThrows(InvalidRequestException.class,
                () -> taskService.create(request("TASK-001", "prj-1", "ghost",
                        LocalDate.of(2024, 1, 1), LocalDate.of(2024, 2, 1),
                        "TODO", 0), "admin@example.com"));
    }

    @Test
    void createRejectsDueDateBeforeStartDate() {
        stubCreateHappyPath("TASK-001");
        when(projectRepository.findById("prj-1"))
                .thenReturn(Optional.of(project("prj-1", "PRJ-001")));

        assertThrows(InvalidRequestException.class,
                () -> taskService.create(request("TASK-001", "prj-1", null,
                        LocalDate.of(2024, 2, 1), LocalDate.of(2024, 1, 1),
                        "TODO", 0), "admin@example.com"));
    }

    @Test
    void createRejectsDuplicateCode() {
        when(taskRepository.existsByTaskCodeIgnoreCase("TASK-001"))
                .thenReturn(true);

        assertThrows(DuplicateResourceException.class,
                () -> taskService.create(request("TASK-001", "prj-1", null,
                        LocalDate.of(2024, 1, 1), LocalDate.of(2024, 2, 1),
                        "TODO", 0), "admin@example.com"));
    }

    // ----- create progress/status consistency ---------------------------

    @Test
    void createForcesCompletedStatusTo100Percent() {
        stubCreateHappyPath("TASK-001");
        when(projectRepository.findById("prj-1"))
                .thenReturn(Optional.of(project("prj-1", "PRJ-001")));
        when(taskRepository.save(any(Task.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(projectRepository.findAllById(any()))
                .thenReturn(List.of(project("prj-1", "PRJ-001")));

        taskService.create(request("TASK-001", "prj-1", null,
                LocalDate.of(2024, 1, 1), LocalDate.of(2024, 2, 1),
                "COMPLETED", 40), "admin@example.com");

        ArgumentCaptor<Task> captor = ArgumentCaptor.forClass(Task.class);
        verify(taskRepository).save(captor.capture());
        assertEquals(100, captor.getValue().getProgress());
        assertEquals("COMPLETED", captor.getValue().getStatus());
    }

    @Test
    void createForcesTodoStatusToZeroPercent() {
        stubCreateHappyPath("TASK-001");
        when(projectRepository.findById("prj-1"))
                .thenReturn(Optional.of(project("prj-1", "PRJ-001")));
        when(taskRepository.save(any(Task.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(projectRepository.findAllById(any()))
                .thenReturn(List.of(project("prj-1", "PRJ-001")));

        taskService.create(request("TASK-001", "prj-1", null,
                LocalDate.of(2024, 1, 1), LocalDate.of(2024, 2, 1),
                "TODO", 60), "admin@example.com");

        ArgumentCaptor<Task> captor = ArgumentCaptor.forClass(Task.class);
        verify(taskRepository).save(captor.capture());
        assertEquals(0, captor.getValue().getProgress());
        assertEquals("TODO", captor.getValue().getStatus());
    }

    @Test
    void createPromotesToCompletedWhenProgressIs100() {
        stubCreateHappyPath("TASK-001");
        when(projectRepository.findById("prj-1"))
                .thenReturn(Optional.of(project("prj-1", "PRJ-001")));
        when(taskRepository.save(any(Task.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(projectRepository.findAllById(any()))
                .thenReturn(List.of(project("prj-1", "PRJ-001")));

        taskService.create(request("TASK-001", "prj-1", null,
                LocalDate.of(2024, 1, 1), LocalDate.of(2024, 2, 1),
                "IN_PROGRESS", 100), "admin@example.com");

        ArgumentCaptor<Task> captor = ArgumentCaptor.forClass(Task.class);
        verify(taskRepository).save(captor.capture());
        assertEquals("COMPLETED", captor.getValue().getStatus());
        assertEquals(100, captor.getValue().getProgress());
    }

    @Test
    void createRecordsCreatedByAndTimestamps() {
        stubCreateHappyPath("TASK-001");
        when(projectRepository.findById("prj-1"))
                .thenReturn(Optional.of(project("prj-1", "PRJ-001")));
        when(employeeRepository.findById("emp-1"))
                .thenReturn(Optional.of(employee("emp-1", "EMP-1", "Asha")));
        when(taskRepository.save(any(Task.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(projectRepository.findAllById(any()))
                .thenReturn(List.of(project("prj-1", "PRJ-001")));
        when(employeeRepository.findAllById(any()))
                .thenReturn(List.of(employee("emp-1", "EMP-1", "Asha")));

        taskService.create(request("TASK-001", "prj-1", "emp-1",
                LocalDate.of(2024, 1, 1), LocalDate.of(2024, 2, 1),
                "IN_PROGRESS", 40), "creator@example.com");

        ArgumentCaptor<Task> captor = ArgumentCaptor.forClass(Task.class);
        verify(taskRepository).save(captor.capture());
        assertEquals("creator@example.com", captor.getValue().getCreatedBy());
        assertNotNull(captor.getValue().getCreatedAt());
    }

    @Test
    void createAutoGeneratesCodeWhenBlank() {
        when(taskRepository.existsByTaskCodeIgnoreCase(anyString()))
                .thenReturn(false);
        when(projectRepository.findById("prj-1"))
                .thenReturn(Optional.of(project("prj-1", "PRJ-001")));
        when(taskRepository.save(any(Task.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(projectRepository.findAllById(any()))
                .thenReturn(List.of(project("prj-1", "PRJ-001")));

        TaskResponse response = taskService.create(
                request(null, "prj-1", null,
                        LocalDate.of(2024, 1, 1), LocalDate.of(2024, 2, 1),
                        "TODO", 0), "admin@example.com");

        assertNotNull(response.taskCode());
        assertTrue(response.taskCode().startsWith("TASK-"));
    }

    // ----- update --------------------------------------------------------

    @Test
    void updateKeepsCreatedByAndCreatedAt() {
        Task existing = storedTask();
        when(taskRepository.findById("task-doc-1"))
                .thenReturn(Optional.of(existing));
        when(projectRepository.findById("prj-1"))
                .thenReturn(Optional.of(project("prj-1", "PRJ-001")));
        when(employeeRepository.findById("emp-1"))
                .thenReturn(Optional.of(employee("emp-1", "EMP-1", "Asha")));
        when(projectRepository.findAllById(any()))
                .thenReturn(List.of(project("prj-1", "PRJ-001")));
        when(employeeRepository.findAllById(any()))
                .thenReturn(List.of(employee("emp-1", "EMP-1", "Asha")));
        when(taskRepository.save(any(Task.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        taskService.update("task-doc-1",
                request("TASK-001", "prj-1", "emp-1",
                        LocalDate.of(2024, 1, 1), LocalDate.of(2024, 2, 1),
                        "IN_REVIEW", 70));

        ArgumentCaptor<Task> captor = ArgumentCaptor.forClass(Task.class);
        verify(taskRepository).save(captor.capture());
        assertEquals("admin@example.com", captor.getValue().getCreatedBy());
        assertEquals(Instant.parse("2024-01-01T00:00:00Z"),
                captor.getValue().getCreatedAt());
        assertEquals("IN_REVIEW", captor.getValue().getStatus());
        assertEquals(70, captor.getValue().getProgress());
    }

    @Test
    void deleteRejectsUnknownTask() {
        when(taskRepository.findById("missing")).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> taskService.delete("missing"));
    }

    // ----- subtasks ------------------------------------------------------

    @Test
    void addSubtaskGeneratesIdAndPersists() {
        Task existing = storedTask();
        when(taskRepository.findById("task-doc-1"))
                .thenReturn(Optional.of(existing));
        when(taskRepository.save(any(Task.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(projectRepository.findAllById(any()))
                .thenReturn(List.of(project("prj-1", "PRJ-001")));
        when(employeeRepository.findAllById(any()))
                .thenReturn(List.of(employee("emp-1", "EMP-1", "Asha")));

        TaskResponse response = taskService.addSubtask("task-doc-1",
                new SubtaskRequest(null, "Write tests", null, null, null, null));

        assertEquals(1, response.subtasks().size());
        assertNotNull(response.subtasks().get(0).getId());
        assertEquals("Write tests", response.subtasks().get(0).getTitle());
    }

    @Test
    void updateSubtaskRejectsUnknownSubtask() {
        when(taskRepository.findById("task-doc-1"))
                .thenReturn(Optional.of(storedTask()));

        assertThrows(ResourceNotFoundException.class,
                () -> taskService.updateSubtask("task-doc-1", "ghost",
                        new SubtaskRequest(null, "x", null, null, null, null)));
    }

    @Test
    void updateSubtaskTogglesCompletion() {
        Task existing = storedTask();
        Subtask subtask = new Subtask();
        subtask.setId("sub-1");
        subtask.setTitle("Write tests");
        existing.setSubtasks(new ArrayList<>(List.of(subtask)));

        when(taskRepository.findById("task-doc-1"))
                .thenReturn(Optional.of(existing));
        when(taskRepository.save(any(Task.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(projectRepository.findAllById(any()))
                .thenReturn(List.of(project("prj-1", "PRJ-001")));
        when(employeeRepository.findAllById(any()))
                .thenReturn(List.of(employee("emp-1", "EMP-1", "Asha")));

        TaskResponse response = taskService.updateSubtask("task-doc-1", "sub-1",
                new SubtaskRequest("sub-1", "Write tests", null, null, null,
                        true));

        assertEquals(1, response.completedSubtaskCount());
        assertTrue(response.subtasks().get(0).isCompleted());
    }

    @Test
    void deleteSubtaskRemovesIt() {
        Task existing = storedTask();
        Subtask subtask = new Subtask();
        subtask.setId("sub-1");
        subtask.setTitle("Write tests");
        existing.setSubtasks(new ArrayList<>(List.of(subtask)));

        when(taskRepository.findById("task-doc-1"))
                .thenReturn(Optional.of(existing));
        when(taskRepository.save(any(Task.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(projectRepository.findAllById(any()))
                .thenReturn(List.of(project("prj-1", "PRJ-001")));
        when(employeeRepository.findAllById(any()))
                .thenReturn(List.of(employee("emp-1", "EMP-1", "Asha")));

        TaskResponse response =
                taskService.deleteSubtask("task-doc-1", "sub-1");

        assertEquals(0, response.subtasks().size());
    }

    @Test
    void deleteSubtaskRejectsUnknown() {
        when(taskRepository.findById("task-doc-1"))
                .thenReturn(Optional.of(storedTask()));

        assertThrows(ResourceNotFoundException.class,
                () -> taskService.deleteSubtask("task-doc-1", "ghost"));
    }

    // ----- list ----------------------------------------------------------

    @Test
    void listMarksOverdueTasks() {
        Task overdue = storedTask();
        overdue.setDueDate(LocalDate.now().minusDays(3));
        overdue.setStatus("IN_PROGRESS");

        Task future = storedTask();
        future.setId("task-doc-2");
        future.setTaskCode("TASK-002");
        future.setDueDate(LocalDate.now().plusDays(3));
        future.setStatus("TODO");

        when(taskRepository.findAllByOrderByCreatedAtDesc())
                .thenReturn(List.of(overdue, future));
        when(projectRepository.findAllById(any()))
                .thenReturn(List.of(project("prj-1", "PRJ-001")));
        when(employeeRepository.findAllById(any()))
                .thenReturn(List.of(employee("emp-1", "EMP-1", "Asha")));

        var page = taskService.list(null, null, null, null, null, false, 1, 20);
        assertEquals(2, page.total());
        assertTrue(page.items().get(0).overdue());
        assertEquals(false, page.items().get(1).overdue());

        var overdueOnly =
                taskService.list(null, null, null, null, null, true, 1, 20);
        assertEquals(1, overdueOnly.total());
        assertEquals("TASK-001", overdueOnly.items().get(0).taskCode());
    }

    @Test
    void listFiltersByProject() {
        Task a = storedTask();
        Task b = storedTask();
        b.setId("task-doc-2");
        b.setTaskCode("TASK-002");
        b.setProjectId("prj-2");

        when(taskRepository.findAllByOrderByCreatedAtDesc())
                .thenReturn(List.of(a, b));
        when(projectRepository.findAllById(any()))
                .thenReturn(List.of(project("prj-1", "PRJ-001"),
                        project("prj-2", "PRJ-002")));
        when(employeeRepository.findAllById(any()))
                .thenReturn(List.of(employee("emp-1", "EMP-1", "Asha")));

        var page = taskService.list(null, null, null, "prj-2", null, false, 1, 20);
        assertEquals(1, page.total());
        assertEquals("prj-2", page.items().get(0).projectId());
    }
}
