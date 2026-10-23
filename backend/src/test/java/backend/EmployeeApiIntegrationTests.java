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
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * End-to-end tests for the Employee API through the real Spring Security
 * filter chain: JWT authentication, the ADMIN-only DELETE rule, validation,
 * duplicate detection and response shapes.
 *
 * The Mongo layer and user lookups are mocked so the suite is hermetic and
 * never touches the real Atlas cluster, regardless of exported env vars.
 */
@SpringBootTest(properties = {
        "spring.mongodb.uri=mongodb://127.0.0.1:27017/priyonix-test",
        "JWT_SECRET=dGVzdC1zZWNyZXQtZm9yLXVuaXQtYnVpbGRzLW9ubHktMzItYnl0ZXM=",
        "ADMIN_EMAIL=",
        "ADMIN_PASSWORD="
})
@AutoConfigureMockMvc
class EmployeeApiIntegrationTests extends PlatformMockDatabaseTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @MockitoBean
    private EmployeeRepository employeeRepository;

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean(answers = Answers.RETURNS_DEEP_STUBS)
    private MongoDatabaseFactory mongoDatabaseFactory;

    private User enabledUser(String role) {
        User user = new User();
        user.setEmail("hr@example.com");
        user.setPassword("{noop}unused");
        user.setRole(role);
        user.setEnabled(true);
        return user;
    }

    private String tokenFor(String role) {
        when(userRepository.findByEmailIgnoreCase("hr@example.com"))
                .thenReturn(Optional.of(enabledUser(role)));
        return jwtService.generateToken("hr@example.com", role);
    }

    private Employee sampleEmployee() {
        Employee employee = new Employee();
        employee.setId("mongo-1");
        employee.setEmployeeId("EMP-001");
        employee.setFullName("Asha Menon");
        employee.setWorkEmail("asha@acme.com");
        employee.setPhone("+91 98765 43210");
        employee.setDepartment("Engineering");
        employee.setDesignation("Senior Developer");
        employee.setJoiningDate(LocalDate.of(2023, 4, 1));
        employee.setSkills(List.of("Java", "Spring Boot"));
        employee.setReportingManager("Ravi Kumar");
        employee.setEmploymentStatus("ACTIVE");
        return employee;
    }

    private String employeeJson(String employeeId, String workEmail) {
        return "{"
                + "\"employeeId\":\"" + employeeId + "\","
                + "\"fullName\":\"Asha Menon\","
                + "\"workEmail\":\"" + workEmail + "\","
                + "\"phone\":\"+91 98765 43210\","
                + "\"department\":\"Engineering\","
                + "\"designation\":\"Senior Developer\","
                + "\"joiningDate\":\"2023-04-01\","
                + "\"skills\":[\"Java\",\"Spring Boot\"],"
                + "\"reportingManager\":\"Ravi Kumar\","
                + "\"employmentStatus\":\"ACTIVE\""
                + "}";
    }

    // ----- authentication ------------------------------------------------

    @Test
    void listRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/employees"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createRequiresAuthentication() throws Exception {
        mockMvc.perform(post("/api/employees")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(employeeJson("EMP-001", "asha@acme.com")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void listReturnsEmployeesForAuthenticatedUser() throws Exception {
        when(employeeRepository.findAllByOrderByFullNameAsc())
                .thenReturn(List.of(sampleEmployee()));

        mockMvc.perform(get("/api/employees")
                        .header("Authorization", "Bearer " + tokenFor("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.count").value(1))
                .andExpect(jsonPath("$.employees[0].employeeId").value("EMP-001"))
                .andExpect(jsonPath("$.employees[0].workEmail")
                        .value("asha@acme.com"))
                .andExpect(jsonPath("$.employees[0].password").doesNotExist());
    }

    @Test
    void searchQueryIsForwardedToTheService() throws Exception {
        when(employeeRepository.findAllByOrderByFullNameAsc())
                .thenReturn(List.of(sampleEmployee()));

        mockMvc.perform(get("/api/employees")
                        .param("search", "engineering")
                        .header("Authorization", "Bearer " + tokenFor("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(1));

        mockMvc.perform(get("/api/employees")
                        .param("search", "marketing")
                        .header("Authorization", "Bearer " + tokenFor("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(0));
    }

    @Test
    void getByIdReturnsEmployee() throws Exception {
        when(employeeRepository.findById("mongo-1"))
                .thenReturn(Optional.of(sampleEmployee()));

        mockMvc.perform(get("/api/employees/mongo-1")
                        .header("Authorization", "Bearer " + tokenFor("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.employee.id").value("mongo-1"));
    }

    @Test
    void getByIdReturnsNotFound() throws Exception {
        when(employeeRepository.findById("missing"))
                .thenReturn(Optional.empty());

        mockMvc.perform(get("/api/employees/missing")
                        .header("Authorization", "Bearer " + tokenFor("ADMIN")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value("ERROR"));
    }

    // ----- create --------------------------------------------------------

    @Test
    void createNormalizesEmailAndReturnsCreated() throws Exception {
        when(employeeRepository.save(any(Employee.class)))
                .thenAnswer(invocation -> {
                    Employee employee = invocation.getArgument(0);
                    employee.setId("mongo-new");
                    return employee;
                });

        mockMvc.perform(post("/api/employees")
                        .header("Authorization", "Bearer " + tokenFor("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(employeeJson("EMP-010", "Asha@ACME.com")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.employee.id").value("mongo-new"))
                .andExpect(jsonPath("$.employee.workEmail")
                        .value("asha@acme.com"))
                .andExpect(jsonPath("$.employee.employmentStatus")
                        .value("ACTIVE"));

        ArgumentCaptor<Employee> captor =
                ArgumentCaptor.forClass(Employee.class);
        verify(employeeRepository).save(captor.capture());
        org.junit.jupiter.api.Assertions.assertNotNull(
                captor.getValue().getCreatedAt());
    }

    @Test
    void createRejectsValidationErrors() throws Exception {
        mockMvc.perform(post("/api/employees")
                        .header("Authorization", "Bearer " + tokenFor("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"employeeId\":\"\",\"fullName\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("ERROR"))
                .andExpect(jsonPath("$.errors").exists());
    }

    @Test
    void createRejectsDuplicateEmployeeId() throws Exception {
        when(employeeRepository.existsByEmployeeIdIgnoreCase("EMP-001"))
                .thenReturn(true);

        mockMvc.perform(post("/api/employees")
                        .header("Authorization", "Bearer " + tokenFor("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(employeeJson("EMP-001", "new@acme.com")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value("ERROR"));
    }

    @Test
    void createRejectsDuplicateWorkEmail() throws Exception {
        when(employeeRepository.existsByEmployeeIdIgnoreCase("EMP-011"))
                .thenReturn(false);
        when(employeeRepository.existsByWorkEmailIgnoreCase("asha@acme.com"))
                .thenReturn(true);

        mockMvc.perform(post("/api/employees")
                        .header("Authorization", "Bearer " + tokenFor("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(employeeJson("EMP-011", "ASHA@acme.com")))
                .andExpect(status().isConflict());
    }

    // ----- update --------------------------------------------------------

    @Test
    void updateReturnsUpdatedEmployee() throws Exception {
        when(employeeRepository.findById("mongo-1"))
                .thenReturn(Optional.of(sampleEmployee()));
        when(employeeRepository.save(any(Employee.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        mockMvc.perform(put("/api/employees/mongo-1")
                        .header("Authorization", "Bearer " + tokenFor("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(employeeJson("EMP-001", "asha@acme.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Employee updated"))
                .andExpect(jsonPath("$.employee.employeeId").value("EMP-001"));
    }

    @Test
    void updateReturnsNotFound() throws Exception {
        when(employeeRepository.findById("missing"))
                .thenReturn(Optional.empty());

        mockMvc.perform(put("/api/employees/missing")
                        .header("Authorization", "Bearer " + tokenFor("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(employeeJson("EMP-001", "asha@acme.com")))
                .andExpect(status().isNotFound());
    }

    // ----- delete: ADMIN only -------------------------------------------

    @Test
    void deleteIsForbiddenForNonAdmin() throws Exception {
        mockMvc.perform(delete("/api/employees/mongo-1")
                        .header("Authorization", "Bearer " + tokenFor("USER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void deleteSucceedsForAdmin() throws Exception {
        when(employeeRepository.findById("mongo-1"))
                .thenReturn(Optional.of(sampleEmployee()));

        mockMvc.perform(delete("/api/employees/mongo-1")
                        .header("Authorization", "Bearer " + tokenFor("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.message").value("Employee deleted"));

        verify(employeeRepository).delete(any(Employee.class));
    }

    @Test
    void deleteReturnsNotFoundForAdmin() throws Exception {
        when(employeeRepository.findById("missing"))
                .thenReturn(Optional.empty());

        mockMvc.perform(delete("/api/employees/missing")
                        .header("Authorization", "Bearer " + tokenFor("ADMIN")))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteRequiresAuthentication() throws Exception {
        mockMvc.perform(delete("/api/employees/mongo-1"))
                .andExpect(status().isUnauthorized());
    }
}
