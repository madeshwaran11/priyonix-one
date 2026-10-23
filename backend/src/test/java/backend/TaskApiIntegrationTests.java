package backend;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * End-to-end tests for the Tasks and Subtasks API through the real Spring
 * Security filter chain: JWT authentication, ADMIN-only DELETE rules,
 * reference validation, progress/status consistency, subtask CRUD and the
 * createdBy-from-principal rule.
 */
@SpringBootTest(properties = {
        "spring.mongodb.uri=mongodb://127.0.0.1:27017/priyonix-test",
        "JWT_SECRET=dGVzdC1zZWNyZXQtZm9yLXVuaXQtYnVpbGRzLW9ubHktMzItYnl0ZXM=",
        "ADMIN_EMAIL=",
        "ADMIN_PASSWORD="
})
@AutoConfigureMockMvc
class TaskApiIntegrationTests extends PlatformMockDatabaseTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @MockitoBean
    private TaskRepository taskRepository;

    @MockitoBean
    private ProjectRepository projectRepository;

    @MockitoBean
    private EmployeeRepository employeeRepository;

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean(answers = Answers.RETURNS_DEEP_STUBS)
    private MongoDatabaseFactory mongoDatabaseFactory;

    // ----- fixtures ------------------------------------------------------

    private User enabledUser(String role) {
        User user = new User();
        user.setEmail("pm@example.com");
        user.setPassword("{noop}unused");
        user.setRole(role);
        user.setEnabled(true);
        return user;
    }

    private String tokenFor(String role) {
        when(userRepository.findByEmailIgnoreCase("pm@example.com"))
                .thenReturn(Optional.of(enabledUser(role)));
        return jwtService.generateToken("pm@example.com", role);
    }

    private Project project() {
        Project project = new Project();
        project.setId("prj-doc-1");
        project.setProjectCode("PRJ-001");
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

    private Task storedTask() {
        Task task = new Task();
        task.setId("task-doc-1");
        task.setTaskCode("TASK-001");
        task.setTitle("Build login page");
        task.setDescription("Implement the auth screen");
        task.setProjectId("prj-doc-1");
        task.setAssigneeId("mongo-1");
        task.setCreatedBy("pm@example.com");
        task.setStartDate(LocalDate.of(2024, 1, 1));
        task.setDueDate(LocalDate.of(2024, 2, 1));
        task.setPriority("HIGH");
        task.setStatus("IN_PROGRESS");
        task.setProgress(40);
        task.setSubtasks(new ArrayList<>());
        return task;
    }

    private String taskJson(
            String code, String projectId, String status, int progress,
            String due) {
        return "{"
                + "\"taskCode\":\"" + code + "\","
                + "\"title\":\"Build login page\","
                + "\"description\":\"Implement the auth screen\","
                + "\"projectId\":\"" + projectId + "\","
                + "\"assigneeId\":\"mongo-1\","
                + "\"startDate\":\"2024-01-01\","
                + "\"dueDate\":\"" + due + "\","
                + "\"priority\":\"HIGH\","
                + "\"status\":\"" + status + "\","
                + "\"progress\":" + progress + ","
                + "\"estimatedHours\":8,"
                + "\"actualHours\":2"
                + "}";
    }

    private void stubTaskResolution() {
        when(projectRepository.findAllById(any()))
                .thenReturn(List.of(project()));
        when(employeeRepository.findAllById(any()))
                .thenReturn(List.of(employee("mongo-1", "EMP-001", "Asha")));
    }

    // ----- authentication ------------------------------------------------

    @Test
    void listRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/tasks"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createRequiresAuthentication() throws Exception {
        mockMvc.perform(post("/api/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(taskJson("TASK-001", "prj-doc-1",
                                "TODO", 0, "2024-02-01")))
                .andExpect(status().isUnauthorized());
    }

    // ----- list / read ---------------------------------------------------

    @Test
    void listReturnsTasksWithResolvedRefs() throws Exception {
        when(taskRepository.findAllByOrderByCreatedAtDesc())
                .thenReturn(List.of(storedTask()));
        stubTaskResolution();

        mockMvc.perform(get("/api/tasks")
                        .header("Authorization", "Bearer " + tokenFor("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.count").value(1))
                .andExpect(jsonPath("$.tasks[0].taskCode").value("TASK-001"))
                .andExpect(jsonPath("$.tasks[0].projectName")
                        .value("Website Redesign"))
                .andExpect(jsonPath("$.tasks[0].assignee.fullName")
                        .value("Asha"))
                .andExpect(jsonPath("$.tasks[0].createdBy")
                        .value("pm@example.com"));
    }

    @Test
    void listFiltersOverdueTasks() throws Exception {
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
        stubTaskResolution();

        mockMvc.perform(get("/api/tasks")
                        .param("overdue", "true")
                        .header("Authorization", "Bearer " + tokenFor("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(1))
                .andExpect(jsonPath("$.tasks[0].taskCode").value("TASK-001"));
    }

    @Test
    void getByIdReturnsNotFound() throws Exception {
        when(taskRepository.findById("missing"))
                .thenReturn(Optional.empty());

        mockMvc.perform(get("/api/tasks/missing")
                        .header("Authorization", "Bearer " + tokenFor("USER")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value("ERROR"));
    }

    // ----- create --------------------------------------------------------

    @Test
    void createRecordsCreatedByFromPrincipal() throws Exception {
        when(taskRepository.existsByTaskCodeIgnoreCase("TASK-001"))
                .thenReturn(false);
        when(projectRepository.findById("prj-doc-1"))
                .thenReturn(Optional.of(project()));
        when(employeeRepository.findById("mongo-1"))
                .thenReturn(Optional.of(employee("mongo-1", "EMP-001", "Asha")));
        when(taskRepository.save(any(Task.class)))
                .thenAnswer(invocation -> {
                    Task task = invocation.getArgument(0);
                    task.setId("task-new");
                    return task;
                });
        stubTaskResolution();

        mockMvc.perform(post("/api/tasks")
                        .header("Authorization", "Bearer " + tokenFor("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(taskJson("TASK-001", "prj-doc-1",
                                "IN_PROGRESS", 40, "2024-02-01")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.task.id").value("task-new"))
                .andExpect(jsonPath("$.task.createdBy")
                        .value("pm@example.com"))
                .andExpect(jsonPath("$.task.progress").value(40));
    }

    @Test
    void createForcesCompletedStatusTo100Percent() throws Exception {
        when(taskRepository.existsByTaskCodeIgnoreCase("TASK-001"))
                .thenReturn(false);
        when(projectRepository.findById("prj-doc-1"))
                .thenReturn(Optional.of(project()));
        when(employeeRepository.findById("mongo-1"))
                .thenReturn(Optional.of(employee("mongo-1", "EMP-001", "Asha")));
        when(taskRepository.save(any(Task.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        stubTaskResolution();

        mockMvc.perform(post("/api/tasks")
                        .header("Authorization", "Bearer " + tokenFor("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(taskJson("TASK-001", "prj-doc-1",
                                "COMPLETED", 30, "2024-02-01")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.task.progress").value(100))
                .andExpect(jsonPath("$.task.status").value("COMPLETED"));
    }

    @Test
    void createRejectsDuplicateCode() throws Exception {
        when(taskRepository.existsByTaskCodeIgnoreCase("TASK-001"))
                .thenReturn(true);

        mockMvc.perform(post("/api/tasks")
                        .header("Authorization", "Bearer " + tokenFor("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(taskJson("TASK-001", "prj-doc-1",
                                "TODO", 0, "2024-02-01")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value("ERROR"));
    }

    @Test
    void createRejectsUnknownProject() throws Exception {
        when(taskRepository.existsByTaskCodeIgnoreCase("TASK-001"))
                .thenReturn(false);
        when(projectRepository.findById("ghost"))
                .thenReturn(Optional.empty());

        mockMvc.perform(post("/api/tasks")
                        .header("Authorization", "Bearer " + tokenFor("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(taskJson("TASK-001", "ghost",
                                "TODO", 0, "2024-02-01")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("ERROR"));
    }

    @Test
    void createRejectsInvalidProgress() throws Exception {
        mockMvc.perform(post("/api/tasks")
                        .header("Authorization", "Bearer " + tokenFor("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(taskJson("TASK-001", "prj-doc-1",
                                "IN_PROGRESS", 150, "2024-02-01")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.progress").exists());
    }

    @Test
    void createRejectsDueDateBeforeStartDate() throws Exception {
        when(taskRepository.existsByTaskCodeIgnoreCase("TASK-001"))
                .thenReturn(false);
        when(projectRepository.findById("prj-doc-1"))
                .thenReturn(Optional.of(project()));

        mockMvc.perform(post("/api/tasks")
                        .header("Authorization", "Bearer " + tokenFor("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(taskJson("TASK-001", "prj-doc-1",
                                "TODO", 0, "2023-12-01")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("ERROR"));
    }

    // ----- update --------------------------------------------------------

    @Test
    void updateReturnsUpdatedTask() throws Exception {
        when(taskRepository.findById("task-doc-1"))
                .thenReturn(Optional.of(storedTask()));
        when(projectRepository.findById("prj-doc-1"))
                .thenReturn(Optional.of(project()));
        when(employeeRepository.findById("mongo-1"))
                .thenReturn(Optional.of(employee("mongo-1", "EMP-001", "Asha")));
        when(taskRepository.save(any(Task.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        stubTaskResolution();

        mockMvc.perform(put("/api/tasks/task-doc-1")
                        .header("Authorization", "Bearer " + tokenFor("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(taskJson("TASK-001", "prj-doc-1",
                                "IN_REVIEW", 70, "2024-02-01")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.task.status").value("IN_REVIEW"))
                .andExpect(jsonPath("$.task.progress").value(70));
    }

    // ----- delete + role checks -----------------------------------------

    @Test
    void deleteForbiddenForNonAdmin() throws Exception {
        mockMvc.perform(delete("/api/tasks/task-doc-1")
                        .header("Authorization", "Bearer " + tokenFor("USER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void deleteAllowedForAdmin() throws Exception {
        when(taskRepository.findById("task-doc-1"))
                .thenReturn(Optional.of(storedTask()));

        mockMvc.perform(delete("/api/tasks/task-doc-1")
                        .header("Authorization", "Bearer " + tokenFor("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"));
    }

    // ----- subtasks ------------------------------------------------------

    @Test
    void addSubtaskReturnsCreated() throws Exception {
        when(taskRepository.findById("task-doc-1"))
                .thenReturn(Optional.of(storedTask()));
        when(taskRepository.save(any(Task.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        stubTaskResolution();

        mockMvc.perform(post("/api/tasks/task-doc-1/subtasks")
                        .header("Authorization", "Bearer " + tokenFor("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Write tests\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.task.subtasks[0].id").isNotEmpty())
                .andExpect(jsonPath("$.task.subtasks[0].title")
                        .value("Write tests"))
                .andExpect(jsonPath("$.task.subtaskCount").value(1));
    }

    @Test
    void updateSubtaskTogglesCompletion() throws Exception {
        Task task = storedTask();
        Subtask subtask = new Subtask();
        subtask.setId("sub-1");
        subtask.setTitle("Write tests");
        task.setSubtasks(new ArrayList<>(List.of(subtask)));

        when(taskRepository.findById("task-doc-1"))
                .thenReturn(Optional.of(task));
        when(taskRepository.save(any(Task.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        stubTaskResolution();

        mockMvc.perform(put("/api/tasks/task-doc-1/subtasks/sub-1")
                        .header("Authorization", "Bearer " + tokenFor("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Write tests\","
                                + "\"completed\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.task.completedSubtaskCount").value(1));
    }

    @Test
    void updateSubtaskReturnsNotFound() throws Exception {
        when(taskRepository.findById("task-doc-1"))
                .thenReturn(Optional.of(storedTask()));

        mockMvc.perform(put("/api/tasks/task-doc-1/subtasks/ghost")
                        .header("Authorization", "Bearer " + tokenFor("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"x\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteSubtaskForbiddenForNonAdmin() throws Exception {
        mockMvc.perform(delete("/api/tasks/task-doc-1/subtasks/sub-1")
                        .header("Authorization", "Bearer " + tokenFor("USER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void deleteSubtaskRemovesIt() throws Exception {
        Task task = storedTask();
        Subtask subtask = new Subtask();
        subtask.setId("sub-1");
        subtask.setTitle("Write tests");
        task.setSubtasks(new ArrayList<>(List.of(subtask)));

        when(taskRepository.findById("task-doc-1"))
                .thenReturn(Optional.of(task));
        when(taskRepository.save(any(Task.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        stubTaskResolution();

        mockMvc.perform(delete("/api/tasks/task-doc-1/subtasks/sub-1")
                        .header("Authorization", "Bearer " + tokenFor("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.task.subtaskCount").value(0));
    }
}
