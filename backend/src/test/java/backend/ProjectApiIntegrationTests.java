package backend;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
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
 * End-to-end tests for the Projects API through the real Spring Security
 * filter chain: JWT authentication, the ADMIN-only DELETE rule, validation,
 * duplicate detection, employee-reference validation, summaries and the
 * safe deletion policy for projects that still contain tasks.
 */
@SpringBootTest(properties = {
        "spring.mongodb.uri=mongodb://127.0.0.1:27017/priyonix-test",
        "JWT_SECRET=dGVzdC1zZWNyZXQtZm9yLXVuaXQtYnVpbGRzLW9ubHktMzItYnl0ZXM=",
        "ADMIN_EMAIL=",
        "ADMIN_PASSWORD="
})
@AutoConfigureMockMvc
class ProjectApiIntegrationTests extends PlatformMockDatabaseTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @MockitoBean
    private ProjectRepository projectRepository;

    @MockitoBean
    private TaskRepository taskRepository;

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

    private Employee employee(String id, String employeeId, String name) {
        Employee employee = new Employee();
        employee.setId(id);
        employee.setEmployeeId(employeeId);
        employee.setFullName(name);
        employee.setWorkEmail(employeeId.toLowerCase() + "@acme.com");
        return employee;
    }

    private Project sampleProject() {
        Project project = new Project();
        project.setId("prj-doc-1");
        project.setProjectCode("PRJ-001");
        project.setName("Website Redesign");
        project.setDescription("Marketing site refresh");
        project.setClientName("Northwind Traders");
        project.setManagerId("mongo-1");
        project.setTeamMemberIds(new ArrayList<>(List.of("mongo-2")));
        project.setStartDate(LocalDate.of(2024, 1, 1));
        project.setDueDate(LocalDate.of(2024, 6, 30));
        project.setPriority("HIGH");
        project.setStatus("ACTIVE");
        project.setMilestones(new ArrayList<>());
        return project;
    }

    private String projectJson(
            String code, String managerId, String start, String due,
            String priority, String status) {
        return "{"
                + "\"projectCode\":\"" + code + "\","
                + "\"name\":\"Website Redesign\","
                + "\"description\":\"Marketing site refresh\","
                + "\"clientName\":\"Northwind Traders\","
                + "\"managerId\":\"" + managerId + "\","
                + "\"teamMemberIds\":[\"mongo-2\"],"
                + "\"startDate\":\"" + start + "\","
                + "\"dueDate\":\"" + due + "\","
                + "\"priority\":\"" + priority + "\","
                + "\"status\":\"" + status + "\","
                + "\"milestones\":[{\"title\":\"Kickoff\","
                + "\"dueDate\":\"2024-01-15\"}]"
                + "}";
    }

    // ----- authentication ------------------------------------------------

    @Test
    void listRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/projects"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createRequiresAuthentication() throws Exception {
        mockMvc.perform(post("/api/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(projectJson("PRJ-001", "mongo-1",
                                "2024-01-01", "2024-06-30", "HIGH", "ACTIVE")))
                .andExpect(status().isUnauthorized());
    }

    // ----- list / read ---------------------------------------------------

    @Test
    void listReturnsProjectsWithResolvedManager() throws Exception {
        when(projectRepository.findAllByOrderByCreatedAtDesc())
                .thenReturn(List.of(sampleProject()));
        when(employeeRepository.findAllById(any()))
                .thenReturn(List.of(employee("mongo-1", "EMP-001", "Asha"),
                        employee("mongo-2", "EMP-002", "Ravi")));

        mockMvc.perform(get("/api/projects")
                        .header("Authorization", "Bearer " + tokenFor("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.count").value(1))
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.projects[0].projectCode")
                        .value("PRJ-001"))
                .andExpect(jsonPath("$.projects[0].manager.fullName")
                        .value("Asha"))
                .andExpect(jsonPath("$.projects[0].teamMembers[0].fullName")
                        .value("Ravi"));
    }

    @Test
    void listFiltersByStatus() throws Exception {
        Project active = sampleProject();
        Project planning = sampleProject();
        planning.setId("prj-doc-2");
        planning.setProjectCode("PRJ-002");
        planning.setStatus("PLANNING");

        when(projectRepository.findAllByOrderByCreatedAtDesc())
                .thenReturn(List.of(active, planning));
        when(employeeRepository.findAllById(any()))
                .thenReturn(List.of(employee("mongo-1", "EMP-001", "Asha"),
                        employee("mongo-2", "EMP-002", "Ravi")));

        mockMvc.perform(get("/api/projects")
                        .param("status", "ACTIVE")
                        .header("Authorization", "Bearer " + tokenFor("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(1))
                .andExpect(jsonPath("$.projects[0].projectCode")
                        .value("PRJ-001"));
    }

    @Test
    void getByIdReturnsNotFound() throws Exception {
        when(projectRepository.findById("missing"))
                .thenReturn(Optional.empty());

        mockMvc.perform(get("/api/projects/missing")
                        .header("Authorization", "Bearer " + tokenFor("USER")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value("ERROR"));
    }

    @Test
    void summaryReturnsCounts() throws Exception {
        when(projectRepository.findById("prj-doc-1"))
                .thenReturn(Optional.of(sampleProject()));

        Task done = new Task();
        done.setStatus("COMPLETED");
        done.setProgress(100);
        done.setDueDate(LocalDate.now().minusDays(3));

        Task open = new Task();
        open.setStatus("IN_PROGRESS");
        open.setProgress(40);
        open.setDueDate(LocalDate.now().minusDays(4));

        when(taskRepository.findByProjectId("prj-doc-1"))
                .thenReturn(List.of(done, open));

        mockMvc.perform(get("/api/projects/prj-doc-1/summary")
                        .header("Authorization", "Bearer " + tokenFor("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.totalTasks").value(2))
                .andExpect(jsonPath("$.summary.completedTasks").value(1))
                .andExpect(jsonPath("$.summary.inProgressTasks").value(1))
                .andExpect(jsonPath("$.summary.overdueTasks").value(1))
                .andExpect(jsonPath("$.summary.progressPercent").value(70));
    }

    @Test
    void tasksForProjectReturnsTasks() throws Exception {
        when(projectRepository.findById("prj-doc-1"))
                .thenReturn(Optional.of(sampleProject()));

        Task task = new Task();
        task.setId("task-doc-1");
        task.setTaskCode("TASK-001");
        task.setTitle("Build login page");
        task.setProjectId("prj-doc-1");
        task.setStatus("IN_PROGRESS");
        task.setPriority("HIGH");
        task.setProgress(40);
        task.setSubtasks(new ArrayList<>());

        when(taskRepository.findByProjectId("prj-doc-1"))
                .thenReturn(List.of(task));
        when(projectRepository.findAllById(any()))
                .thenReturn(List.of(sampleProject()));

        mockMvc.perform(get("/api/projects/prj-doc-1/tasks")
                        .header("Authorization", "Bearer " + tokenFor("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(1))
                .andExpect(jsonPath("$.tasks[0].taskCode").value("TASK-001"));
    }

    // ----- create --------------------------------------------------------

    @Test
    void createReturnsCreatedProject() throws Exception {
        when(projectRepository.existsByProjectCodeIgnoreCase("PRJ-001"))
                .thenReturn(false);
        when(employeeRepository.findById("mongo-1"))
                .thenReturn(Optional.of(employee("mongo-1", "EMP-001", "Asha")));
        when(employeeRepository.findById("mongo-2"))
                .thenReturn(Optional.of(employee("mongo-2", "EMP-002", "Ravi")));
        when(employeeRepository.findAllById(any()))
                .thenReturn(List.of(employee("mongo-1", "EMP-001", "Asha"),
                        employee("mongo-2", "EMP-002", "Ravi")));
        when(projectRepository.save(any(Project.class)))
                .thenAnswer(invocation -> {
                    Project project = invocation.getArgument(0);
                    project.setId("prj-new");
                    return project;
                });

        mockMvc.perform(post("/api/projects")
                        .header("Authorization", "Bearer " + tokenFor("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(projectJson("PRJ-001", "mongo-1",
                                "2024-01-01", "2024-06-30", "HIGH", "ACTIVE")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.project.id").value("prj-new"))
                .andExpect(jsonPath("$.project.projectCode").value("PRJ-001"))
                .andExpect(jsonPath("$.project.milestones[0].id")
                        .isNotEmpty());
    }

    @Test
    void createRejectsDuplicateCode() throws Exception {
        when(projectRepository.existsByProjectCodeIgnoreCase("PRJ-001"))
                .thenReturn(true);

        mockMvc.perform(post("/api/projects")
                        .header("Authorization", "Bearer " + tokenFor("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(projectJson("PRJ-001", "mongo-1",
                                "2024-01-01", "2024-06-30", "HIGH", "ACTIVE")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value("ERROR"));
    }

    @Test
    void createRejectsUnknownManager() throws Exception {
        when(projectRepository.existsByProjectCodeIgnoreCase("PRJ-001"))
                .thenReturn(false);
        when(employeeRepository.findById("ghost"))
                .thenReturn(Optional.empty());

        mockMvc.perform(post("/api/projects")
                        .header("Authorization", "Bearer " + tokenFor("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(projectJson("PRJ-001", "ghost",
                                "2024-01-01", "2024-06-30", "HIGH", "ACTIVE")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("ERROR"));
    }

    @Test
    void createRejectsUnknownTeamMember() throws Exception {
        when(projectRepository.existsByProjectCodeIgnoreCase("PRJ-001"))
                .thenReturn(false);
        when(employeeRepository.findById("mongo-1"))
                .thenReturn(Optional.of(employee("mongo-1", "EMP-001", "Asha")));
        when(employeeRepository.findById("mongo-2"))
                .thenReturn(Optional.empty());

        mockMvc.perform(post("/api/projects")
                        .header("Authorization", "Bearer " + tokenFor("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(projectJson("PRJ-001", "mongo-1",
                                "2024-01-01", "2024-06-30", "HIGH", "ACTIVE")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("ERROR"));
    }

    @Test
    void createRejectsDueDateBeforeStartDate() throws Exception {
        when(projectRepository.existsByProjectCodeIgnoreCase("PRJ-001"))
                .thenReturn(false);

        mockMvc.perform(post("/api/projects")
                        .header("Authorization", "Bearer " + tokenFor("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(projectJson("PRJ-001", "mongo-1",
                                "2024-06-30", "2024-01-01", "HIGH", "ACTIVE")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("ERROR"));
    }

    @Test
    void createRejectsInvalidStatus() throws Exception {
        when(projectRepository.existsByProjectCodeIgnoreCase("PRJ-001"))
                .thenReturn(false);

        mockMvc.perform(post("/api/projects")
                        .header("Authorization", "Bearer " + tokenFor("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(projectJson("PRJ-001", "mongo-1",
                                "2024-01-01", "2024-06-30", "HIGH", "NOPE")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("ERROR"));
    }

    @Test
    void createRejectsMissingName() throws Exception {
        String body = "{"
                + "\"projectCode\":\"PRJ-001\","
                + "\"managerId\":\"mongo-1\","
                + "\"startDate\":\"2024-01-01\","
                + "\"dueDate\":\"2024-06-30\","
                + "\"priority\":\"HIGH\","
                + "\"status\":\"ACTIVE\""
                + "}";

        mockMvc.perform(post("/api/projects")
                        .header("Authorization", "Bearer " + tokenFor("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.name").exists());
    }

    // ----- update --------------------------------------------------------

    @Test
    void updateReturnsUpdatedProject() throws Exception {
        when(projectRepository.findById("prj-doc-1"))
                .thenReturn(Optional.of(sampleProject()));
        when(employeeRepository.findById("mongo-1"))
                .thenReturn(Optional.of(employee("mongo-1", "EMP-001", "Asha")));
        when(employeeRepository.findById("mongo-2"))
                .thenReturn(Optional.of(employee("mongo-2", "EMP-002", "Ravi")));
        when(employeeRepository.findAllById(any()))
                .thenReturn(List.of(employee("mongo-1", "EMP-001", "Asha"),
                        employee("mongo-2", "EMP-002", "Ravi")));
        when(projectRepository.save(any(Project.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        mockMvc.perform(put("/api/projects/prj-doc-1")
                        .header("Authorization", "Bearer " + tokenFor("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(projectJson("PRJ-001", "mongo-1",
                                "2024-01-01", "2024-06-30", "CRITICAL",
                                "ON_HOLD")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.project.status").value("ON_HOLD"))
                .andExpect(jsonPath("$.project.priority").value("CRITICAL"));
    }

    @Test
    void updateReturnsNotFound() throws Exception {
        when(projectRepository.findById("missing"))
                .thenReturn(Optional.empty());

        mockMvc.perform(put("/api/projects/missing")
                        .header("Authorization", "Bearer " + tokenFor("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(projectJson("PRJ-001", "mongo-1",
                                "2024-01-01", "2024-06-30", "HIGH", "ACTIVE")))
                .andExpect(status().isNotFound());
    }

    // ----- delete + role checks -----------------------------------------

    @Test
    void deleteForbiddenForNonAdmin() throws Exception {
        mockMvc.perform(delete("/api/projects/prj-doc-1")
                        .header("Authorization", "Bearer " + tokenFor("USER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void deleteAllowedForAdmin() throws Exception {
        when(projectRepository.findById("prj-doc-1"))
                .thenReturn(Optional.of(sampleProject()));
        when(taskRepository.countByProjectId("prj-doc-1")).thenReturn(0L);

        mockMvc.perform(delete("/api/projects/prj-doc-1")
                        .header("Authorization", "Bearer " + tokenFor("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"));
    }

    @Test
    void deleteWithTasksRequiresCascade() throws Exception {
        when(projectRepository.findById("prj-doc-1"))
                .thenReturn(Optional.of(sampleProject()));
        when(taskRepository.countByProjectId("prj-doc-1")).thenReturn(2L);

        mockMvc.perform(delete("/api/projects/prj-doc-1")
                        .header("Authorization", "Bearer " + tokenFor("ADMIN")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value("ERROR"));
    }

    @Test
    void cascadeDeleteRemovesTasks() throws Exception {
        when(projectRepository.findById("prj-doc-1"))
                .thenReturn(Optional.of(sampleProject()));
        when(taskRepository.countByProjectId("prj-doc-1")).thenReturn(2L);

        mockMvc.perform(delete("/api/projects/prj-doc-1")
                        .param("cascade", "true")
                        .header("Authorization", "Bearer " + tokenFor("ADMIN")))
                .andExpect(status().isOk());

        verify(taskRepository).deleteByProjectId("prj-doc-1");
    }
}
