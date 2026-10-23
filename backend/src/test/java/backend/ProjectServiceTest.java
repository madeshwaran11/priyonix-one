package backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProjectServiceTest {

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private EmployeeRepository employeeRepository;

    @Mock
    private TaskRepository taskRepository;

    private ProjectService projectService;

    @BeforeEach
    void setUp() {
        projectService = new ProjectService(
                projectRepository, employeeRepository, taskRepository);
    }

    private Employee employee(String id, String employeeId, String name) {
        Employee employee = new Employee();
        employee.setId(id);
        employee.setEmployeeId(employeeId);
        employee.setFullName(name);
        employee.setWorkEmail(employeeId.toLowerCase() + "@acme.com");
        return employee;
    }

    private ProjectRequest request(
            String code, String managerId, List<String> team,
            LocalDate start, LocalDate due, String priority, String status,
            List<MilestoneRequest> milestones) {
        return new ProjectRequest(
                code, "Website Redesign", "Marketing site refresh",
                "Northwind Traders", managerId, team, start, due,
                priority, status, milestones);
    }

    private ProjectRequest validRequest() {
        return request("PRJ-001", "emp-1", List.of("emp-2"),
                LocalDate.of(2024, 1, 1), LocalDate.of(2024, 6, 30),
                "HIGH", "ACTIVE", null);
    }

    private Project storedProject() {
        Project project = new Project();
        project.setId("prj-doc-1");
        project.setProjectCode("PRJ-001");
        project.setName("Website Redesign");
        project.setManagerId("emp-1");
        project.setTeamMemberIds(List.of("emp-2"));
        project.setStartDate(LocalDate.of(2024, 1, 1));
        project.setDueDate(LocalDate.of(2024, 6, 30));
        project.setPriority("HIGH");
        project.setStatus("ACTIVE");
        project.setCreatedAt(Instant.parse("2024-01-01T00:00:00Z"));
        project.setMilestones(new java.util.ArrayList<>());
        return project;
    }

    // ----- create --------------------------------------------------------

    @Test
    void createRejectsDuplicateCode() {
        when(projectRepository.existsByProjectCodeIgnoreCase("PRJ-001"))
                .thenReturn(true);

        assertThrows(DuplicateResourceException.class,
                () -> projectService.create(validRequest()));

        verify(projectRepository, never()).save(any(Project.class));
    }

    @Test
    void createRejectsUnknownManager() {
        when(projectRepository.existsByProjectCodeIgnoreCase("PRJ-001"))
                .thenReturn(false);
        when(employeeRepository.findById("emp-1"))
                .thenReturn(Optional.empty());

        assertThrows(InvalidRequestException.class,
                () -> projectService.create(validRequest()));

        verify(projectRepository, never()).save(any(Project.class));
    }

    @Test
    void createRejectsUnknownTeamMember() {
        when(projectRepository.existsByProjectCodeIgnoreCase("PRJ-001"))
                .thenReturn(false);
        when(employeeRepository.findById("emp-1"))
                .thenReturn(Optional.of(employee("emp-1", "EMP-1", "Asha")));
        when(employeeRepository.findById("ghost"))
                .thenReturn(Optional.empty());

        ProjectRequest request = request("PRJ-001", "emp-1", List.of("ghost"),
                LocalDate.of(2024, 1, 1), LocalDate.of(2024, 6, 30),
                "HIGH", "ACTIVE", null);

        assertThrows(InvalidRequestException.class,
                () -> projectService.create(request));
    }

    @Test
    void createRejectsDueDateBeforeStartDate() {
        when(projectRepository.existsByProjectCodeIgnoreCase("PRJ-001"))
                .thenReturn(false);

        ProjectRequest request = request("PRJ-001", "emp-1", List.of(),
                LocalDate.of(2024, 6, 30), LocalDate.of(2024, 1, 1),
                "HIGH", "ACTIVE", null);

        assertThrows(InvalidRequestException.class,
                () -> projectService.create(request));
    }

    @Test
    void createRejectsInvalidStatus() {
        when(projectRepository.existsByProjectCodeIgnoreCase("PRJ-001"))
                .thenReturn(false);

        ProjectRequest request = request("PRJ-001", "emp-1", List.of(),
                LocalDate.of(2024, 1, 1), LocalDate.of(2024, 6, 30),
                "HIGH", "NOT_A_STATUS", null);

        assertThrows(InvalidRequestException.class,
                () -> projectService.create(request));
    }

    @Test
    void createNormalizesDefaultsAndStampsTimestamps() {
        when(projectRepository.existsByProjectCodeIgnoreCase("PRJ-001"))
                .thenReturn(false);
        when(employeeRepository.findById("emp-1"))
                .thenReturn(Optional.of(employee("emp-1", "EMP-1", "Asha")));
        when(employeeRepository.findById("emp-2"))
                .thenReturn(Optional.of(employee("emp-2", "EMP-2", "Ravi")));
        when(projectRepository.save(any(Project.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Employee manager = employee("emp-1", "EMP-1", "Asha");
        Employee member = employee("emp-2", "EMP-2", "Ravi");
        when(employeeRepository.findAllById(any()))
                .thenReturn(List.of(manager, member));

        projectService.create(request("PRJ-001", "emp-1", List.of("emp-2"),
                LocalDate.of(2024, 1, 1), LocalDate.of(2024, 6, 30),
                null, null,
                List.of(new MilestoneRequest(null, "Kickoff", null,
                        LocalDate.of(2024, 1, 15), null))));

        ArgumentCaptor<Project> captor =
                ArgumentCaptor.forClass(Project.class);
        verify(projectRepository).save(captor.capture());
        Project saved = captor.getValue();

        assertEquals("PRJ-001", saved.getProjectCode());
        assertEquals("MEDIUM", saved.getPriority());
        assertEquals("PLANNING", saved.getStatus());
        assertNotNull(saved.getCreatedAt());
        assertNotNull(saved.getUpdatedAt());
        assertEquals(1, saved.getMilestones().size());
        assertNotNull(saved.getMilestones().get(0).getId());
    }

    // ----- update --------------------------------------------------------

    @Test
    void updateRejectsDuplicateCodeWhenChanged() {
        when(projectRepository.findById("prj-doc-1"))
                .thenReturn(Optional.of(storedProject()));
        when(projectRepository.existsByProjectCodeIgnoreCase("PRJ-002"))
                .thenReturn(true);

        ProjectRequest request = request("PRJ-002", "emp-1", List.of(),
                LocalDate.of(2024, 1, 1), LocalDate.of(2024, 6, 30),
                "HIGH", "ACTIVE", null);

        assertThrows(DuplicateResourceException.class,
                () -> projectService.update("prj-doc-1", request));

        verify(projectRepository, never()).save(any(Project.class));
    }

    @Test
    void updateKeepsCreatedAtAndSetsUpdatedAt() {
        Project existing = storedProject();
        existing.setUpdatedAt(Instant.parse("2024-01-01T00:00:00Z"));

        when(projectRepository.findById("prj-doc-1"))
                .thenReturn(Optional.of(existing));
        when(employeeRepository.findById("emp-1"))
                .thenReturn(Optional.of(employee("emp-1", "EMP-1", "Asha")));
        when(employeeRepository.findAllById(any()))
                .thenReturn(List.of(employee("emp-1", "EMP-1", "Asha")));
        when(projectRepository.save(any(Project.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        projectService.update("prj-doc-1",
                request("PRJ-001", "emp-1", List.of(),
                        LocalDate.of(2024, 1, 1), LocalDate.of(2024, 6, 30),
                        "HIGH", "ACTIVE", null));

        ArgumentCaptor<Project> captor =
                ArgumentCaptor.forClass(Project.class);
        verify(projectRepository).save(captor.capture());

        assertEquals(Instant.parse("2024-01-01T00:00:00Z"),
                captor.getValue().getCreatedAt());
        assertTrue(captor.getValue().getUpdatedAt()
                .isAfter(Instant.parse("2024-01-01T00:00:00Z")));
    }

    // ----- delete --------------------------------------------------------

    @Test
    void deleteRejectsProjectWithTasksWithoutCascade() {
        when(projectRepository.findById("prj-doc-1"))
                .thenReturn(Optional.of(storedProject()));
        when(taskRepository.countByProjectId("prj-doc-1")).thenReturn(3L);

        assertThrows(ConflictException.class,
                () -> projectService.delete("prj-doc-1", false));

        verify(projectRepository, never()).delete(any(Project.class));
        verify(taskRepository, never()).deleteByProjectId(any());
    }

    @Test
    void deleteCascadesTasksWhenRequested() {
        Project project = storedProject();
        when(projectRepository.findById("prj-doc-1"))
                .thenReturn(Optional.of(project));
        when(taskRepository.countByProjectId("prj-doc-1")).thenReturn(2L);

        projectService.delete("prj-doc-1", true);

        verify(taskRepository).deleteByProjectId("prj-doc-1");
        verify(projectRepository).delete(project);
    }

    @Test
    void deleteRejectsUnknownProject() {
        when(projectRepository.findById("missing")).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> projectService.delete("missing", false));
    }

    // ----- summary -------------------------------------------------------

    @Test
    void summaryComputesProgressAndCounts() {
        Project project = storedProject();
        Milestone done = new Milestone();
        done.setCompleted(true);
        Milestone open = new Milestone();
        open.setCompleted(false);
        project.setMilestones(new java.util.ArrayList<>(List.of(done, open)));

        when(projectRepository.findById("prj-doc-1"))
                .thenReturn(Optional.of(project));

        Task t1 = new Task();
        t1.setStatus("COMPLETED");
        t1.setProgress(100);
        t1.setDueDate(LocalDate.now().minusDays(4));

        Task t2 = new Task();
        t2.setStatus("IN_PROGRESS");
        t2.setProgress(50);
        t2.setDueDate(LocalDate.now().minusDays(2));

        Task t3 = new Task();
        t3.setStatus("TODO");
        t3.setProgress(0);
        t3.setDueDate(LocalDate.now().plusDays(5));

        when(taskRepository.findByProjectId("prj-doc-1"))
                .thenReturn(List.of(t1, t2, t3));

        ProjectSummaryResponse summary = projectService.summary("prj-doc-1");

        assertEquals(3, summary.totalTasks());
        assertEquals(1, summary.completedTasks());
        assertEquals(1, summary.inProgressTasks());
        assertEquals(1, summary.overdueTasks());
        assertEquals(50, summary.progressPercent());
        assertEquals(2, summary.totalMilestones());
        assertEquals(1, summary.completedMilestones());
        assertEquals(50, summary.milestoneProgressPercent());
    }

    // ----- list ----------------------------------------------------------

    @Test
    void listFiltersByStatusAndSearch() {
        Project active = storedProject();
        Project planning = storedProject();
        planning.setId("prj-doc-2");
        planning.setProjectCode("PRJ-002");
        planning.setName("CRM Rollout");
        planning.setStatus("PLANNING");

        when(projectRepository.findAllByOrderByCreatedAtDesc())
                .thenReturn(List.of(active, planning));
        when(employeeRepository.findAllById(any()))
                .thenReturn(List.of(employee("emp-1", "EMP-1", "Asha"),
                        employee("emp-2", "EMP-2", "Ravi")));

        PagedResult<ProjectResponse> byStatus =
                projectService.list(null, "ACTIVE", null, 1, 20);
        assertEquals(1, byStatus.total());
        assertEquals("PRJ-001", byStatus.items().get(0).projectCode());

        PagedResult<ProjectResponse> bySearch =
                projectService.list("crm", null, null, 1, 20);
        assertEquals(1, bySearch.total());
        assertEquals("PRJ-002", bySearch.items().get(0).projectCode());
    }

    @Test
    void listPaginates() {
        Project a = storedProject();
        Project b = storedProject();
        b.setId("prj-doc-2");
        b.setProjectCode("PRJ-002");
        Project c = storedProject();
        c.setId("prj-doc-3");
        c.setProjectCode("PRJ-003");

        when(projectRepository.findAllByOrderByCreatedAtDesc())
                .thenReturn(List.of(a, b, c));
        when(employeeRepository.findAllById(any()))
                .thenReturn(List.of(employee("emp-1", "EMP-1", "Asha"),
                        employee("emp-2", "EMP-2", "Ravi")));

        PagedResult<ProjectResponse> page1 =
                projectService.list(null, null, null, 1, 2);

        assertEquals(3, page1.total());
        assertEquals(2, page1.items().size());
        assertEquals(2, page1.totalPages());
    }
}
