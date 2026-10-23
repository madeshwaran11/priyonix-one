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
 * End-to-end tests for the CRM Leads API: authentication, validation, the
 * ADMIN-only DELETE rule and - most importantly - the lead-to-client
 * conversion rules (only WON leads convert once, and an existing client with
 * the same email is reused instead of duplicated).
 */
@SpringBootTest(properties = {
        "spring.mongodb.uri=mongodb://127.0.0.1:27017/priyonix-test",
        "JWT_SECRET=dGVzdC1zZWNyZXQtZm9yLXVuaXQtYnVpbGRzLW9ubHktMzItYnl0ZXM=",
        "ADMIN_EMAIL=",
        "ADMIN_PASSWORD="
})
@AutoConfigureMockMvc
class LeadApiIntegrationTests extends PlatformMockDatabaseTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @MockitoBean
    private LeadRepository leadRepository;

    @MockitoBean
    private ClientRepository clientRepository;

    @MockitoBean
    private FollowUpRepository followUpRepository;

    @MockitoBean
    private CampaignRepository campaignRepository;

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

    private Lead sampleLead(String status) {
        Lead lead = new Lead();
        lead.setId("lead-doc-1");
        lead.setName("Priya Sharma");
        lead.setCompany("Northwind Traders");
        lead.setEmail("priya@northwind.com");
        lead.setStatus(status);
        lead.setPriority("HIGH");
        lead.setAssignedToId("mongo-1");
        lead.setCreatedAt(Instant.parse("2024-01-01T00:00:00Z"));
        return lead;
    }

    private String leadJson(String status, String priority, String assigneeId) {
        return leadJson(status, priority, assigneeId, null);
    }

    private String leadJson(
            String status, String priority, String assigneeId,
            String campaignId) {

        return "{"
                + "\"name\":\"Priya Sharma\","
                + "\"company\":\"Northwind Traders\","
                + "\"email\":\"priya@northwind.com\","
                + "\"phone\":\"+91 90000 00000\","
                + "\"source\":\"Website\","
                + "\"status\":\"" + status + "\","
                + "\"priority\":\"" + priority + "\","
                + "\"assignedToId\":\"" + assigneeId + "\","
                + (campaignId == null
                        ? ""
                        : "\"campaignId\":\"" + campaignId + "\",")
                + "\"estimatedValue\":50000,"
                + "\"followUpDate\":\"2024-07-01\","
                + "\"notes\":\"Interested\""
                + "}";
    }

    private Campaign campaign() {
        Campaign campaign = new Campaign();
        campaign.setId("campaign-1");
        campaign.setName("Q4 Webinar");
        campaign.setChannel("EMAIL");
        campaign.setStatus("ACTIVE");
        return campaign;
    }

    // ----- authentication ------------------------------------------------

    @Test
    void listRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/leads"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createRequiresAuthentication() throws Exception {
        mockMvc.perform(post("/api/leads")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(leadJson("NEW", "HIGH", "mongo-1")))
                .andExpect(status().isUnauthorized());
    }

    // ----- list / read ---------------------------------------------------

    @Test
    void listReturnsLeadsWithResolvedAssignee() throws Exception {
        when(leadRepository.findAllByOrderByCreatedAtDesc())
                .thenReturn(List.of(sampleLead("NEW")));
        when(employeeRepository.findAllById(any()))
                .thenReturn(List.of(employee("mongo-1", "EMP-001", "Asha")));

        mockMvc.perform(get("/api/leads")
                        .header("Authorization", "Bearer " + tokenFor("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.count").value(1))
                .andExpect(jsonPath("$.leads[0].name").value("Priya Sharma"))
                .andExpect(jsonPath("$.leads[0].assignedTo.fullName")
                        .value("Asha"));
    }

    @Test
    void listFiltersByStatus() throws Exception {
        Lead open = sampleLead("NEW");
        Lead won = sampleLead("WON");
        won.setId("lead-doc-2");

        when(leadRepository.findAllByOrderByCreatedAtDesc())
                .thenReturn(List.of(open, won));
        when(employeeRepository.findAllById(any()))
                .thenReturn(List.of(employee("mongo-1", "EMP-001", "Asha")));

        mockMvc.perform(get("/api/leads")
                        .param("status", "WON")
                        .header("Authorization", "Bearer " + tokenFor("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(1))
                .andExpect(jsonPath("$.leads[0].id").value("lead-doc-2"));
    }

    @Test
    void listRejectsInvalidStatus() throws Exception {
        mockMvc.perform(get("/api/leads")
                        .param("status", "NOPE")
                        .header("Authorization", "Bearer " + tokenFor("USER")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("ERROR"));
    }

    @Test
    void getByIdReturnsNotFound() throws Exception {
        when(leadRepository.findById("missing")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/leads/missing")
                        .header("Authorization", "Bearer " + tokenFor("USER")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value("ERROR"));
    }

    // ----- create --------------------------------------------------------

    @Test
    void createReturnsCreatedLead() throws Exception {
        when(employeeRepository.findById("mongo-1"))
                .thenReturn(Optional.of(employee("mongo-1", "EMP-001", "Asha")));
        when(employeeRepository.findAllById(any()))
                .thenReturn(List.of(employee("mongo-1", "EMP-001", "Asha")));
        when(leadRepository.save(any(Lead.class)))
                .thenAnswer(invocation -> {
                    Lead lead = invocation.getArgument(0);
                    lead.setId("lead-new");
                    return lead;
                });

        mockMvc.perform(post("/api/leads")
                        .header("Authorization", "Bearer " + tokenFor("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(leadJson("NEW", "HIGH", "mongo-1")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.lead.id").value("lead-new"))
                .andExpect(jsonPath("$.lead.status").value("NEW"));
    }

    @Test
    void createWithCampaignPersistsAttributionAndResolvesName() throws Exception {
        when(employeeRepository.findById("mongo-1"))
                .thenReturn(Optional.of(employee("mongo-1", "EMP-001", "Asha")));
        when(employeeRepository.findAllById(any()))
                .thenReturn(List.of(employee("mongo-1", "EMP-001", "Asha")));
        when(campaignRepository.findById("campaign-1"))
                .thenReturn(Optional.of(campaign()));
        when(campaignRepository.findAllById(any()))
                .thenReturn(List.of(campaign()));
        when(leadRepository.save(any(Lead.class)))
                .thenAnswer(invocation -> {
                    Lead lead = invocation.getArgument(0);
                    lead.setId("lead-new");
                    return lead;
                });

        mockMvc.perform(post("/api/leads")
                        .header("Authorization", "Bearer " + tokenFor("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(leadJson(
                                "NEW", "HIGH", "mongo-1", "campaign-1")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.lead.campaignId").value("campaign-1"))
                .andExpect(jsonPath("$.lead.campaignName").value("Q4 Webinar"));
    }

    @Test
    void createRejectsUnknownCampaign() throws Exception {
        when(employeeRepository.findById("mongo-1"))
                .thenReturn(Optional.of(employee("mongo-1", "EMP-001", "Asha")));
        when(campaignRepository.findById("missing-campaign"))
                .thenReturn(Optional.empty());

        mockMvc.perform(post("/api/leads")
                        .header("Authorization", "Bearer " + tokenFor("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(leadJson(
                                "NEW", "HIGH", "mongo-1", "missing-campaign")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        "Lead campaign references an unknown campaign: "
                                + "missing-campaign"));
    }

    @Test
    void createRejectsInvalidStatus() throws Exception {
        mockMvc.perform(post("/api/leads")
                        .header("Authorization", "Bearer " + tokenFor("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(leadJson("NOPE", "HIGH", "mongo-1")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createRejectsInvalidPriority() throws Exception {
        mockMvc.perform(post("/api/leads")
                        .header("Authorization", "Bearer " + tokenFor("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(leadJson("NEW", "URGENT", "mongo-1")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createRejectsUnknownAssignee() throws Exception {
        when(employeeRepository.findById("ghost")).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/leads")
                        .header("Authorization", "Bearer " + tokenFor("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(leadJson("NEW", "HIGH", "ghost")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createRejectsMissingName() throws Exception {
        String body = "{"
                + "\"email\":\"priya@northwind.com\","
                + "\"status\":\"NEW\""
                + "}";

        mockMvc.perform(post("/api/leads")
                        .header("Authorization", "Bearer " + tokenFor("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.name").exists());
    }

    // ----- update --------------------------------------------------------

    @Test
    void updateReturnsUpdatedLead() throws Exception {
        when(leadRepository.findById("lead-doc-1"))
                .thenReturn(Optional.of(sampleLead("NEW")));
        when(employeeRepository.findById("mongo-1"))
                .thenReturn(Optional.of(employee("mongo-1", "EMP-001", "Asha")));
        when(employeeRepository.findAllById(any()))
                .thenReturn(List.of(employee("mongo-1", "EMP-001", "Asha")));
        when(leadRepository.save(any(Lead.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        mockMvc.perform(put("/api/leads/lead-doc-1")
                        .header("Authorization", "Bearer " + tokenFor("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(leadJson("QUALIFIED", "CRITICAL", "mongo-1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lead.status").value("QUALIFIED"))
                .andExpect(jsonPath("$.lead.priority").value("CRITICAL"));
    }

    // ----- conversion ----------------------------------------------------

    @Test
    void convertRequiresWonLead() throws Exception {
        when(leadRepository.findById("lead-doc-1"))
                .thenReturn(Optional.of(sampleLead("CONTACTED")));

        mockMvc.perform(post("/api/leads/lead-doc-1/convert")
                        .header("Authorization", "Bearer " + tokenFor("USER")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("ERROR"));
    }

    @Test
    void convertCreatesClientForWonLead() throws Exception {
        when(leadRepository.findById("lead-doc-1"))
                .thenReturn(Optional.of(sampleLead("WON")));
        when(clientRepository.findByEmailIgnoreCase("priya@northwind.com"))
                .thenReturn(Optional.empty());
        when(clientRepository.save(any(Client.class)))
                .thenAnswer(invocation -> {
                    Client client = invocation.getArgument(0);
                    client.setId("client-new");
                    return client;
                });
        when(leadRepository.save(any(Lead.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        mockMvc.perform(post("/api/leads/lead-doc-1/convert")
                        .header("Authorization", "Bearer " + tokenFor("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created").value(true))
                .andExpect(jsonPath("$.client.id").value("client-new"))
                .andExpect(jsonPath("$.lead.convertedClientId")
                        .value("client-new"));
    }

    @Test
    void convertReusesExistingClientByEmail() throws Exception {
        when(leadRepository.findById("lead-doc-1"))
                .thenReturn(Optional.of(sampleLead("WON")));

        Client existing = new Client();
        existing.setId("client-existing");
        existing.setName("Priya Sharma");
        existing.setEmail("priya@northwind.com");
        when(clientRepository.findByEmailIgnoreCase("priya@northwind.com"))
                .thenReturn(Optional.of(existing));
        when(leadRepository.save(any(Lead.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        mockMvc.perform(post("/api/leads/lead-doc-1/convert")
                        .header("Authorization", "Bearer " + tokenFor("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created").value(false))
                .andExpect(jsonPath("$.client.id").value("client-existing"));

        verify(clientRepository, org.mockito.Mockito.never())
                .save(any(Client.class));
    }

    @Test
    void convertRejectsAlreadyConvertedLead() throws Exception {
        Lead lead = sampleLead("WON");
        lead.setConvertedClientId("client-existing");
        when(leadRepository.findById("lead-doc-1"))
                .thenReturn(Optional.of(lead));

        mockMvc.perform(post("/api/leads/lead-doc-1/convert")
                        .header("Authorization", "Bearer " + tokenFor("USER")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value("ERROR"));
    }

    @Test
    void convertReturnsNotFound() throws Exception {
        when(leadRepository.findById("missing")).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/leads/missing/convert")
                        .header("Authorization", "Bearer " + tokenFor("USER")))
                .andExpect(status().isNotFound());
    }

    // ----- follow-ups ----------------------------------------------------

    @Test
    void leadFollowUpsReturnsActivities() throws Exception {
        when(leadRepository.findById("lead-doc-1"))
                .thenReturn(Optional.of(sampleLead("NEW")));

        FollowUp followUp = new FollowUp();
        followUp.setId("fu-1");
        followUp.setActivityType("EMAIL");
        followUp.setLeadId("lead-doc-1");
        followUp.setDueDate(java.time.LocalDate.now().plusDays(2));

        when(followUpRepository.findByLeadId("lead-doc-1"))
                .thenReturn(List.of(followUp));

        mockMvc.perform(get("/api/leads/lead-doc-1/follow-ups")
                        .header("Authorization", "Bearer " + tokenFor("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(1))
                .andExpect(jsonPath("$.followUps[0].activityType")
                        .value("EMAIL"));
    }

    // ----- delete + role checks -----------------------------------------

    @Test
    void deleteForbiddenForNonAdmin() throws Exception {
        mockMvc.perform(delete("/api/leads/lead-doc-1")
                        .header("Authorization", "Bearer " + tokenFor("USER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void deleteAllowedForAdmin() throws Exception {
        when(leadRepository.findById("lead-doc-1"))
                .thenReturn(Optional.of(sampleLead("NEW")));
        when(followUpRepository.findByLeadId("lead-doc-1"))
                .thenReturn(List.of());

        mockMvc.perform(delete("/api/leads/lead-doc-1")
                        .header("Authorization", "Bearer " + tokenFor("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"));
    }
}
