package backend;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
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
 * API tests for marketing campaigns: authentication, ADMIN-only deletion,
 * validation, lifecycle status changes, filters and lead-safe deletion.
 */
@SpringBootTest(properties = {
        "spring.mongodb.uri=mongodb://127.0.0.1:27017/priyonix-test",
        "JWT_SECRET=dGVzdC1zZWNyZXQtZm9yLXVuaXQtYnVpbGRzLW9ubHktMzItYnl0ZXM=",
        "ADMIN_EMAIL=",
        "ADMIN_PASSWORD="
})
@AutoConfigureMockMvc
class CampaignApiIntegrationTests extends PlatformMockDatabaseTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @MockitoBean
    private CampaignRepository campaignRepository;

    @MockitoBean
    private LeadRepository leadRepository;

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

    private Campaign sampleCampaign(String status) {
        Campaign campaign = new Campaign();
        campaign.setId("campaign-1");
        campaign.setName("Q4 Webinar");
        campaign.setDescription("Partner demand generation");
        campaign.setChannel("EMAIL");
        campaign.setStatus(status);
        campaign.setStartDate(LocalDate.of(2026, 10, 1));
        campaign.setEndDate(LocalDate.of(2026, 12, 31));
        campaign.setBudget(new BigDecimal("50000"));
        campaign.setSpend(new BigDecimal("12000"));
        campaign.setCreatedAt(Instant.parse("2024-01-01T00:00:00Z"));
        campaign.setUpdatedAt(Instant.parse("2024-01-01T00:00:00Z"));
        return campaign;
    }

    private Lead attributedLead() {
        Lead lead = new Lead();
        lead.setId("lead-1");
        lead.setName("Priya Sharma");
        lead.setCampaignId("campaign-1");
        lead.setStatus("WON");
        lead.setEstimatedValue(new BigDecimal("50000"));
        return lead;
    }

    private String campaignJson(
            String name, String channel, String status,
            String startDate, String endDate) {

        return "{"
                + "\"name\":\"" + name + "\","
                + "\"description\":\"Demand gen\","
                + "\"channel\":\"" + channel + "\","
                + "\"status\":\"" + status + "\","
                + "\"startDate\":\"" + startDate + "\","
                + "\"endDate\":\"" + endDate + "\","
                + "\"budget\":50000,"
                + "\"spend\":12000"
                + "}";
    }

    private String validCampaignJson() {
        return campaignJson(
                "Q4 Webinar", "EMAIL", "PLANNED", "2026-10-01", "2026-12-31");
    }

    // ----- authentication / RBAC -----------------------------------------

    @Test
    void listRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/campaigns"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createRequiresAuthentication() throws Exception {
        mockMvc.perform(post("/api/campaigns")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validCampaignJson()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void deleteRequiresAdminRole() throws Exception {
        String token = tokenFor("USER");

        mockMvc.perform(delete("/api/campaigns/campaign-1")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    // ----- create --------------------------------------------------------

    @Test
    void createReturnsCreatedAndNormalizesValues() throws Exception {
        String token = tokenFor("USER");

        when(campaignRepository.findByNameIgnoreCase("Q4 Webinar"))
                .thenReturn(Optional.empty());
        when(campaignRepository.save(any(Campaign.class)))
                .thenAnswer(invocation -> {
                    Campaign saved = invocation.getArgument(0);
                    saved.setId("campaign-new");
                    return saved;
                });

        mockMvc.perform(post("/api/campaigns")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(campaignJson(
                                "  Q4 Webinar  ", "email", "",
                                "2026-10-01", "2026-12-31")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.campaign.id").value("campaign-new"))
                .andExpect(jsonPath("$.campaign.name").value("Q4 Webinar"))
                .andExpect(jsonPath("$.campaign.channel").value("EMAIL"))
                .andExpect(jsonPath("$.campaign.status").value("PLANNED"))
                .andExpect(jsonPath("$.campaign.leadCount").value(0));
    }

    @Test
    void createRejectsEndDateBeforeStartDate() throws Exception {
        String token = tokenFor("USER");

        mockMvc.perform(post("/api/campaigns")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(campaignJson(
                                "Q4 Webinar", "EMAIL", "PLANNED",
                                "2026-12-31", "2026-10-01")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        "Campaign end date must be on or after the start date"));
    }

    @Test
    void createRejectsUnknownChannel() throws Exception {
        String token = tokenFor("USER");

        mockMvc.perform(post("/api/campaigns")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(campaignJson(
                                "Q4 Webinar", "TIKTOK", "PLANNED",
                                "2026-10-01", "2026-12-31")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString(
                                "Channel must be one of")));
    }

    @Test
    void createRejectsMissingRequiredFields() throws Exception {
        String token = tokenFor("USER");

        mockMvc.perform(post("/api/campaigns")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Q4 Webinar\",\"channel\":\"EMAIL\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.errors.startDate").value(
                        "Start date is required"))
                .andExpect(jsonPath("$.errors.endDate").value(
                        "End date is required"));
    }

    @Test
    void createRejectsDuplicateName() throws Exception {
        String token = tokenFor("USER");

        when(campaignRepository.findByNameIgnoreCase("Q4 Webinar"))
                .thenReturn(Optional.of(sampleCampaign("PLANNED")));

        mockMvc.perform(post("/api/campaigns")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validCampaignJson()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(
                        "A campaign named Q4 Webinar already exists"));
    }

    // ----- list / read ---------------------------------------------------

    @Test
    void listReturnsCampaignsWithDerivedMetrics() throws Exception {
        String token = tokenFor("USER");

        when(campaignRepository.findAllByOrderByCreatedAtDesc())
                .thenReturn(List.of(sampleCampaign("ACTIVE")));
        when(leadRepository.findByCampaignIdIn(any()))
                .thenReturn(List.of(attributedLead()));

        mockMvc.perform(get("/api/campaigns?status=ACTIVE&channel=EMAIL")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.campaigns[0].name").value("Q4 Webinar"))
                .andExpect(jsonPath("$.campaigns[0].leadCount").value(1))
                .andExpect(jsonPath("$.campaigns[0].convertedLeadCount").value(0))
                .andExpect(jsonPath("$.campaigns[0].wonLeadCount").value(1))
                .andExpect(jsonPath("$.campaigns[0].conversionRate").value(0.0))
                .andExpect(jsonPath("$.campaigns[0].pipelineValue").value(50000));
    }

    @Test
    void listRejectsUnknownStatusFilter() throws Exception {
        String token = tokenFor("USER");

        mockMvc.perform(get("/api/campaigns?status=RUNNING")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getByIdReturnsNotFoundForUnknownCampaign() throws Exception {
        String token = tokenFor("USER");

        when(campaignRepository.findById("missing"))
                .thenReturn(Optional.empty());

        mockMvc.perform(get("/api/campaigns/missing")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void getByIdReturnsCampaign() throws Exception {
        String token = tokenFor("USER");

        when(campaignRepository.findById("campaign-1"))
                .thenReturn(Optional.of(sampleCampaign("ACTIVE")));

        mockMvc.perform(get("/api/campaigns/campaign-1")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.campaign.id").value("campaign-1"))
                .andExpect(jsonPath("$.campaign.channel").value("EMAIL"));
    }

    // ----- update --------------------------------------------------------

    @Test
    void updateCampaignPersistsChanges() throws Exception {
        String token = tokenFor("USER");
        Campaign stored = sampleCampaign("PLANNED");

        when(campaignRepository.findById("campaign-1"))
                .thenReturn(Optional.of(stored));
        when(campaignRepository.findByNameIgnoreCase("Q4 Webinar"))
                .thenReturn(Optional.of(stored));
        when(campaignRepository.save(any(Campaign.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        mockMvc.perform(put("/api/campaigns/campaign-1")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(campaignJson(
                                "Q4 Webinar", "SOCIAL", "ACTIVE",
                                "2026-11-01", "2026-12-31")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Campaign updated"))
                .andExpect(jsonPath("$.campaign.channel").value("SOCIAL"))
                .andExpect(jsonPath("$.campaign.status").value("ACTIVE"))
                .andExpect(jsonPath("$.campaign.startDate").value("2026-11-01"));
    }

    @Test
    void updateRejectsDuplicateNameOwnedByAnotherCampaign() throws Exception {
        String token = tokenFor("USER");
        Campaign stored = sampleCampaign("PLANNED");
        Campaign other = sampleCampaign("PLANNED");
        other.setId("campaign-2");
        other.setName("Existing");

        when(campaignRepository.findById("campaign-1"))
                .thenReturn(Optional.of(stored));
        when(campaignRepository.findByNameIgnoreCase("Existing"))
                .thenReturn(Optional.of(other));

        mockMvc.perform(put("/api/campaigns/campaign-1")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(campaignJson(
                                "Existing", "EMAIL", "PLANNED",
                                "2026-10-01", "2026-12-31")))
                .andExpect(status().isConflict());
    }

    @Test
    void updateStatusChangesLifecycle() throws Exception {
        String token = tokenFor("USER");
        Campaign stored = sampleCampaign("ACTIVE");

        when(campaignRepository.findById("campaign-1"))
                .thenReturn(Optional.of(stored));
        when(campaignRepository.save(any(Campaign.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        mockMvc.perform(patch("/api/campaigns/campaign-1/status")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"paused\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.campaign.status").value("PAUSED"));
    }

    @Test
    void updateStatusRejectsUnknownStatus() throws Exception {
        String token = tokenFor("USER");
        Campaign stored = sampleCampaign("ACTIVE");

        when(campaignRepository.findById("campaign-1"))
                .thenReturn(Optional.of(stored));

        mockMvc.perform(patch("/api/campaigns/campaign-1/status")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"RUNNING\"}"))
                .andExpect(status().isBadRequest());
    }

    // ----- delete --------------------------------------------------------

    @Test
    void deleteWithoutLeadsSucceedsForAdmin() throws Exception {
        String token = tokenFor("ADMIN");
        Campaign stored = sampleCampaign("PLANNED");

        when(campaignRepository.findById("campaign-1"))
                .thenReturn(Optional.of(stored));

        mockMvc.perform(delete("/api/campaigns/campaign-1")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Campaign deleted"));

        verify(campaignRepository).delete(stored);
    }

    @Test
    void deleteWithAttributedLeadsRequiresDetachFlag() throws Exception {
        String token = tokenFor("ADMIN");
        Campaign stored = sampleCampaign("PLANNED");

        when(campaignRepository.findById("campaign-1"))
                .thenReturn(Optional.of(stored));
        when(leadRepository.findByCampaignId("campaign-1"))
                .thenReturn(List.of(attributedLead()));

        mockMvc.perform(delete("/api/campaigns/campaign-1")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isConflict());

        verify(campaignRepository, never()).delete(any(Campaign.class));
    }

    @Test
    void deleteWithDetachFlagClearsLeadAttribution() throws Exception {
        String token = tokenFor("ADMIN");
        Campaign stored = sampleCampaign("PLANNED");
        Lead lead = attributedLead();

        when(campaignRepository.findById("campaign-1"))
                .thenReturn(Optional.of(stored));
        when(leadRepository.findByCampaignId("campaign-1"))
                .thenReturn(List.of(lead));

        mockMvc.perform(delete("/api/campaigns/campaign-1?detachLeads=true")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value(
                        "Campaign deleted and attributed leads detached"));

        verify(leadRepository).saveAll(any());
        verify(campaignRepository).delete(stored);
    }
}
