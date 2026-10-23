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
 * End-to-end tests for the Follow-ups API: authentication, the ADMIN-only
 * DELETE rule, validation of the lead/client link and activity type, and the
 * server-computed overdue flag.
 */
@SpringBootTest(properties = {
        "spring.mongodb.uri=mongodb://127.0.0.1:27017/priyonix-test",
        "JWT_SECRET=dGVzdC1zZWNyZXQtZm9yLXVuaXQtYnVpbGRzLW9ubHktMzItYnl0ZXM=",
        "ADMIN_EMAIL=",
        "ADMIN_PASSWORD="
})
@AutoConfigureMockMvc
class FollowUpApiIntegrationTests extends PlatformMockDatabaseTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @MockitoBean
    private FollowUpRepository followUpRepository;

    @MockitoBean
    private LeadRepository leadRepository;

    @MockitoBean
    private ClientRepository clientRepository;

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

    private FollowUp followUp(String clientId, String leadId, LocalDate due) {
        FollowUp followUp = new FollowUp();
        followUp.setId("fu-doc-1");
        followUp.setActivityType("CALL");
        followUp.setDueDate(due);
        followUp.setClientId(clientId);
        followUp.setLeadId(leadId);
        return followUp;
    }

    private Client client(String id, String name) {
        Client client = new Client();
        client.setId(id);
        client.setName(name);
        client.setEmail("hello@northwind.com");
        client.setStatus("ACTIVE");
        return client;
    }

    private String followUpJson(
            String activityType, String dueDate, Boolean completed,
            String leadId, String clientId) {

        StringBuilder json = new StringBuilder("{");
        json.append("\"activityType\":\"").append(activityType).append("\",");
        json.append("\"dueDate\":\"").append(dueDate).append("\",");
        json.append("\"notes\":\"Call back\",");
        if (completed != null) {
            json.append("\"completed\":").append(completed).append(",");
        }
        if (leadId != null) {
            json.append("\"leadId\":\"").append(leadId).append("\",");
        }
        if (clientId != null) {
            json.append("\"clientId\":\"").append(clientId).append("\",");
        }
        // Trim the trailing comma, then close.
        json.setLength(json.length() - 1);
        json.append("}");
        return json.toString();
    }

    // ----- authentication ------------------------------------------------

    @Test
    void listRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/follow-ups"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createRequiresAuthentication() throws Exception {
        mockMvc.perform(post("/api/follow-ups")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(followUpJson("CALL", "2024-07-01", null, null,
                                "client-doc-1")))
                .andExpect(status().isUnauthorized());
    }

    // ----- list / read ---------------------------------------------------

    @Test
    void listReturnsFollowUpsWithResolvedNames() throws Exception {
        when(followUpRepository.findAllByOrderByCreatedAtDesc())
                .thenReturn(List.of(followUp("client-doc-1", null,
                        LocalDate.now().plusDays(1))));
        when(clientRepository.findAllById(any()))
                .thenReturn(List.of(client("client-doc-1", "Northwind")));

        mockMvc.perform(get("/api/follow-ups")
                        .header("Authorization", "Bearer " + tokenFor("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.count").value(1))
                .andExpect(jsonPath("$.followUps[0].clientName")
                        .value("Northwind"));
    }

    @Test
    void listOverdueOnlyFilters() throws Exception {
        FollowUp overdue = followUp(null, "lead-doc-1",
                LocalDate.now().minusDays(2));
        overdue.setId("fu-overdue");

        FollowUp future = followUp(null, "lead-doc-1",
                LocalDate.now().plusDays(2));
        future.setId("fu-future");

        when(followUpRepository.findAllByOrderByCreatedAtDesc())
                .thenReturn(List.of(overdue, future));
        when(leadRepository.findAllById(any()))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/follow-ups")
                        .param("overdue", "true")
                        .header("Authorization", "Bearer " + tokenFor("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(1))
                .andExpect(jsonPath("$.followUps[0].id").value("fu-overdue"))
                .andExpect(jsonPath("$.followUps[0].overdue").value(true));
    }

    @Test
    void listRejectsInvalidCompletedFilter() throws Exception {
        mockMvc.perform(get("/api/follow-ups")
                        .param("completed", "maybe")
                        .header("Authorization", "Bearer " + tokenFor("USER")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("ERROR"));
    }

    @Test
    void getByIdReturnsNotFound() throws Exception {
        when(followUpRepository.findById("missing"))
                .thenReturn(Optional.empty());

        mockMvc.perform(get("/api/follow-ups/missing")
                        .header("Authorization", "Bearer " + tokenFor("USER")))
                .andExpect(status().isNotFound());
    }

    // ----- create --------------------------------------------------------

    @Test
    void createReturnsCreatedFollowUp() throws Exception {
        when(clientRepository.findById("client-doc-1"))
                .thenReturn(Optional.of(client("client-doc-1", "Northwind")));
        when(followUpRepository.save(any(FollowUp.class)))
                .thenAnswer(invocation -> {
                    FollowUp saved = invocation.getArgument(0);
                    saved.setId("fu-new");
                    return saved;
                });

        mockMvc.perform(post("/api/follow-ups")
                        .header("Authorization", "Bearer " + tokenFor("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(followUpJson("call", "2024-07-01", false, null,
                                "client-doc-1")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.followUp.id").value("fu-new"))
                .andExpect(jsonPath("$.followUp.activityType").value("CALL"));
    }

    @Test
    void createRejectsBothLeadAndClient() throws Exception {
        mockMvc.perform(post("/api/follow-ups")
                        .header("Authorization", "Bearer " + tokenFor("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(followUpJson("CALL", "2024-07-01", false,
                                "lead-doc-1", "client-doc-1")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createRejectsNeitherLeadNorClient() throws Exception {
        mockMvc.perform(post("/api/follow-ups")
                        .header("Authorization", "Bearer " + tokenFor("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"activityType\":\"CALL\","
                                + "\"dueDate\":\"2024-07-01\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createRejectsUnknownLead() throws Exception {
        when(leadRepository.findById("ghost")).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/follow-ups")
                        .header("Authorization", "Bearer " + tokenFor("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(followUpJson("CALL", "2024-07-01", false,
                                "ghost", null)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createRejectsInvalidActivityType() throws Exception {
        mockMvc.perform(post("/api/follow-ups")
                        .header("Authorization", "Bearer " + tokenFor("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(followUpJson("TEXT", "2024-07-01", false, null,
                                "client-doc-1")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createRejectsMissingDueDate() throws Exception {
        mockMvc.perform(post("/api/follow-ups")
                        .header("Authorization", "Bearer " + tokenFor("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"activityType\":\"CALL\","
                                + "\"clientId\":\"client-doc-1\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.dueDate").exists());
    }

    // ----- update --------------------------------------------------------

    @Test
    void updateReturnsUpdatedFollowUp() throws Exception {
        when(followUpRepository.findById("fu-doc-1"))
                .thenReturn(Optional.of(followUp("client-doc-1", null,
                        LocalDate.now().plusDays(1))));
        when(clientRepository.findById("client-doc-1"))
                .thenReturn(Optional.of(client("client-doc-1", "Northwind")));
        when(followUpRepository.save(any(FollowUp.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        mockMvc.perform(put("/api/follow-ups/fu-doc-1")
                        .header("Authorization", "Bearer " + tokenFor("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(followUpJson("MEETING", "2024-08-01", true, null,
                                "client-doc-1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.followUp.activityType").value("MEETING"))
                .andExpect(jsonPath("$.followUp.completed").value(true));
    }

    // ----- delete + role checks -----------------------------------------

    @Test
    void deleteForbiddenForNonAdmin() throws Exception {
        mockMvc.perform(delete("/api/follow-ups/fu-doc-1")
                        .header("Authorization", "Bearer " + tokenFor("USER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void deleteAllowedForAdmin() throws Exception {
        when(followUpRepository.findById("fu-doc-1"))
                .thenReturn(Optional.of(followUp("client-doc-1", null,
                        LocalDate.now())));

        mockMvc.perform(delete("/api/follow-ups/fu-doc-1")
                        .header("Authorization", "Bearer " + tokenFor("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"));
    }
}
