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

import java.time.Instant;
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
 * End-to-end tests for the Clients API through the real Spring Security filter
 * chain: JWT authentication, the ADMIN-only DELETE rule, validation, duplicate
 * email detection, employee-reference validation and the safe deletion policy
 * for clients that still have follow-ups.
 */
@SpringBootTest(properties = {
        "spring.mongodb.uri=mongodb://127.0.0.1:27017/priyonix-test",
        "JWT_SECRET=dGVzdC1zZWNyZXQtZm9yLXVuaXQtYnVpbGRzLW9ubHktMzItYnl0ZXM=",
        "ADMIN_EMAIL=",
        "ADMIN_PASSWORD="
})
@AutoConfigureMockMvc
class ClientApiIntegrationTests extends PlatformMockDatabaseTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @MockitoBean
    private ClientRepository clientRepository;

    @MockitoBean
    private FollowUpRepository followUpRepository;

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

    private Client sampleClient() {
        Client client = new Client();
        client.setId("client-doc-1");
        client.setName("Northwind Traders");
        client.setCompanyName("Northwind Ltd");
        client.setEmail("hello@northwind.com");
        client.setStatus("ACTIVE");
        client.setAssignedToId("mongo-1");
        client.setCreatedAt(Instant.parse("2024-01-01T00:00:00Z"));
        return client;
    }

    private String clientJson(
            String email, String ownerId, String status) {
        return "{"
                + "\"name\":\"Northwind Traders\","
                + "\"companyName\":\"Northwind Ltd\","
                + "\"email\":\"" + email + "\","
                + "\"phone\":\"+91 90000 00000\","
                + "\"address\":\"12 Market Street\","
                + "\"industry\":\"Retail\","
                + "\"status\":\"" + status + "\","
                + "\"assignedToId\":\"" + ownerId + "\","
                + "\"notes\":\"Key account\""
                + "}";
    }

    // ----- authentication ------------------------------------------------

    @Test
    void listRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/clients"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createRequiresAuthentication() throws Exception {
        mockMvc.perform(post("/api/clients")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(clientJson("hello@northwind.com", "mongo-1",
                                "ACTIVE")))
                .andExpect(status().isUnauthorized());
    }

    // ----- list / read ---------------------------------------------------

    @Test
    void listReturnsClientsWithResolvedOwner() throws Exception {
        when(clientRepository.findAllByOrderByCreatedAtDesc())
                .thenReturn(List.of(sampleClient()));
        when(employeeRepository.findAllById(any()))
                .thenReturn(List.of(employee("mongo-1", "EMP-001", "Asha")));

        mockMvc.perform(get("/api/clients")
                        .header("Authorization", "Bearer " + tokenFor("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.count").value(1))
                .andExpect(jsonPath("$.clients[0].name")
                        .value("Northwind Traders"))
                .andExpect(jsonPath("$.clients[0].assignedTo.fullName")
                        .value("Asha"));
    }

    @Test
    void listFiltersByStatus() throws Exception {
        Client active = sampleClient();
        Client prospect = sampleClient();
        prospect.setId("client-doc-2");
        prospect.setName("Contoso");
        prospect.setEmail("hi@contoso.com");
        prospect.setStatus("PROSPECT");

        when(clientRepository.findAllByOrderByCreatedAtDesc())
                .thenReturn(List.of(active, prospect));
        when(employeeRepository.findAllById(any()))
                .thenReturn(List.of(employee("mongo-1", "EMP-001", "Asha")));

        mockMvc.perform(get("/api/clients")
                        .param("status", "PROSPECT")
                        .header("Authorization", "Bearer " + tokenFor("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(1))
                .andExpect(jsonPath("$.clients[0].name").value("Contoso"));
    }

    @Test
    void listRejectsInvalidStatus() throws Exception {
        mockMvc.perform(get("/api/clients")
                        .param("status", "NOPE")
                        .header("Authorization", "Bearer " + tokenFor("USER")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("ERROR"));
    }

    @Test
    void getByIdReturnsNotFound() throws Exception {
        when(clientRepository.findById("missing")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/clients/missing")
                        .header("Authorization", "Bearer " + tokenFor("USER")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value("ERROR"));
    }

    // ----- create --------------------------------------------------------

    @Test
    void createReturnsCreatedClient() throws Exception {
        when(clientRepository.existsByEmailIgnoreCase("hello@northwind.com"))
                .thenReturn(false);
        when(employeeRepository.findById("mongo-1"))
                .thenReturn(Optional.of(employee("mongo-1", "EMP-001", "Asha")));
        when(employeeRepository.findAllById(any()))
                .thenReturn(List.of(employee("mongo-1", "EMP-001", "Asha")));
        when(clientRepository.save(any(Client.class)))
                .thenAnswer(invocation -> {
                    Client client = invocation.getArgument(0);
                    client.setId("client-new");
                    return client;
                });

        mockMvc.perform(post("/api/clients")
                        .header("Authorization", "Bearer " + tokenFor("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(clientJson("Hello@Northwind.com", "mongo-1",
                                "ACTIVE")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.client.id").value("client-new"))
                .andExpect(jsonPath("$.client.email")
                        .value("hello@northwind.com"));
    }

    @Test
    void createRejectsDuplicateEmail() throws Exception {
        when(clientRepository.existsByEmailIgnoreCase("hello@northwind.com"))
                .thenReturn(true);

        mockMvc.perform(post("/api/clients")
                        .header("Authorization", "Bearer " + tokenFor("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(clientJson("hello@northwind.com", "mongo-1",
                                "ACTIVE")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value("ERROR"));
    }

    @Test
    void createRejectsInvalidEmailFormat() throws Exception {
        mockMvc.perform(post("/api/clients")
                        .header("Authorization", "Bearer " + tokenFor("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(clientJson("not-an-email", "mongo-1",
                                "ACTIVE")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.email").exists());
    }

    @Test
    void createRejectsMissingName() throws Exception {
        String body = "{"
                + "\"email\":\"hello@northwind.com\","
                + "\"status\":\"ACTIVE\""
                + "}";

        mockMvc.perform(post("/api/clients")
                        .header("Authorization", "Bearer " + tokenFor("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.name").exists());
    }

    @Test
    void createRejectsUnknownOwner() throws Exception {
        when(clientRepository.existsByEmailIgnoreCase("hello@northwind.com"))
                .thenReturn(false);
        when(employeeRepository.findById("ghost")).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/clients")
                        .header("Authorization", "Bearer " + tokenFor("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(clientJson("hello@northwind.com", "ghost",
                                "ACTIVE")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("ERROR"));
    }

    // ----- update --------------------------------------------------------

    @Test
    void updateReturnsUpdatedClient() throws Exception {
        when(clientRepository.findById("client-doc-1"))
                .thenReturn(Optional.of(sampleClient()));
        when(employeeRepository.findById("mongo-1"))
                .thenReturn(Optional.of(employee("mongo-1", "EMP-001", "Asha")));
        when(employeeRepository.findAllById(any()))
                .thenReturn(List.of(employee("mongo-1", "EMP-001", "Asha")));
        when(clientRepository.save(any(Client.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        mockMvc.perform(put("/api/clients/client-doc-1")
                        .header("Authorization", "Bearer " + tokenFor("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(clientJson("hello@northwind.com", "mongo-1",
                                "CHURNED")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.client.status").value("CHURNED"));
    }

    @Test
    void updateReturnsNotFound() throws Exception {
        when(clientRepository.findById("missing")).thenReturn(Optional.empty());

        mockMvc.perform(put("/api/clients/missing")
                        .header("Authorization", "Bearer " + tokenFor("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(clientJson("hello@northwind.com", "mongo-1",
                                "ACTIVE")))
                .andExpect(status().isNotFound());
    }

    // ----- follow-ups ----------------------------------------------------

    @Test
    void clientFollowUpsReturnsActivities() throws Exception {
        when(clientRepository.findById("client-doc-1"))
                .thenReturn(Optional.of(sampleClient()));

        FollowUp followUp = new FollowUp();
        followUp.setId("fu-1");
        followUp.setActivityType("CALL");
        followUp.setClientId("client-doc-1");
        followUp.setDueDate(java.time.LocalDate.now().minusDays(2));

        when(followUpRepository.findByClientId("client-doc-1"))
                .thenReturn(List.of(followUp));

        mockMvc.perform(get("/api/clients/client-doc-1/follow-ups")
                        .header("Authorization", "Bearer " + tokenFor("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(1))
                .andExpect(jsonPath("$.followUps[0].overdue").value(true));
    }

    // ----- delete + role checks -----------------------------------------

    @Test
    void deleteForbiddenForNonAdmin() throws Exception {
        mockMvc.perform(delete("/api/clients/client-doc-1")
                        .header("Authorization", "Bearer " + tokenFor("USER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void deleteAllowedForAdmin() throws Exception {
        when(clientRepository.findById("client-doc-1"))
                .thenReturn(Optional.of(sampleClient()));
        when(followUpRepository.findByClientId("client-doc-1"))
                .thenReturn(List.of());

        mockMvc.perform(delete("/api/clients/client-doc-1")
                        .header("Authorization", "Bearer " + tokenFor("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"));
    }

    @Test
    void deleteWithFollowUpsRequiresCascade() throws Exception {
        when(clientRepository.findById("client-doc-1"))
                .thenReturn(Optional.of(sampleClient()));
        when(followUpRepository.findByClientId("client-doc-1"))
                .thenReturn(List.of(new FollowUp(), new FollowUp()));

        mockMvc.perform(delete("/api/clients/client-doc-1")
                        .header("Authorization", "Bearer " + tokenFor("ADMIN")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value("ERROR"));
    }

    @Test
    void cascadeDeleteRemovesFollowUps() throws Exception {
        when(clientRepository.findById("client-doc-1"))
                .thenReturn(Optional.of(sampleClient()));
        when(followUpRepository.findByClientId("client-doc-1"))
                .thenReturn(List.of(new FollowUp()));

        mockMvc.perform(delete("/api/clients/client-doc-1")
                        .param("cascade", "true")
                        .header("Authorization", "Bearer " + tokenFor("ADMIN")))
                .andExpect(status().isOk());

        verify(followUpRepository).deleteByClientId("client-doc-1");
    }
}
