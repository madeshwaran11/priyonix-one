package backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
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
class CampaignServiceTest {

    @Mock
    private CampaignRepository campaignRepository;

    @Mock
    private LeadRepository leadRepository;

    private CampaignService campaignService;

    @BeforeEach
    void setUp() {
        campaignService = new CampaignService(
                campaignRepository, leadRepository);
    }

    private Campaign campaign(
            String id, String name, String status, String channel,
            LocalDate start, LocalDate end) {

        Campaign campaign = new Campaign();
        campaign.setId(id);
        campaign.setName(name);
        campaign.setStatus(status);
        campaign.setChannel(channel);
        campaign.setStartDate(start);
        campaign.setEndDate(end);
        campaign.setCreatedAt(Instant.parse("2024-01-01T00:00:00Z"));
        return campaign;
    }

    private CampaignRequest request(
            String name, String channel, String status,
            LocalDate start, LocalDate end) {

        return new CampaignRequest(
                name, "Description", channel, status, start, end,
                new BigDecimal("10000"), new BigDecimal("2500"));
    }

    private Lead lead(
            String id, String campaignId, String status, String clientId,
            String value) {

        Lead lead = new Lead();
        lead.setId(id);
        lead.setName("Lead " + id);
        lead.setCampaignId(campaignId);
        lead.setStatus(status);
        lead.setConvertedClientId(clientId);
        lead.setEstimatedValue(value == null ? null : new BigDecimal(value));
        return lead;
    }

    @Test
    void createNormalizesDefaultsAndTrimsName() {
        when(campaignRepository.findByNameIgnoreCase("Q4 Webinar"))
                .thenReturn(Optional.empty());
        when(campaignRepository.save(any(Campaign.class)))
                .thenAnswer(invocation -> {
                    Campaign saved = invocation.getArgument(0);
                    saved.setId("campaign-new");
                    return saved;
                });

        CampaignResponse response = campaignService.create(new CampaignRequest(
                "  Q4 Webinar  ", "  Demand gen  ", "", "",
                LocalDate.of(2026, 10, 1), LocalDate.of(2026, 12, 31),
                new BigDecimal("50000"), null));

        assertEquals("Q4 Webinar", response.name());
        assertEquals("PLANNED", response.status());
        assertEquals("OTHER", response.channel());
        assertEquals("Demand gen", response.description());
        assertNull(response.spend());
        assertEquals(0, response.leadCount());
        assertEquals(0.0, response.conversionRate());
    }

    @Test
    void createRejectsEndDateBeforeStartDate() {
        assertThrows(InvalidRequestException.class,
                () -> campaignService.create(request(
                        "Q4 Webinar", "EMAIL", "PLANNED",
                        LocalDate.of(2026, 12, 31),
                        LocalDate.of(2026, 10, 1))));

        verify(campaignRepository, never()).save(any(Campaign.class));
    }

    @Test
    void createRejectsDuplicateName() {
        when(campaignRepository.findByNameIgnoreCase("Q4 Webinar"))
                .thenReturn(Optional.of(campaign(
                        "campaign-existing", "Q4 Webinar", "PLANNED", "EMAIL",
                        LocalDate.now(), LocalDate.now().plusDays(5))));

        assertThrows(DuplicateResourceException.class,
                () -> campaignService.create(request(
                        "Q4 Webinar", "EMAIL", "PLANNED",
                        LocalDate.now(), LocalDate.now().plusDays(5))));
    }

    @Test
    void createRejectsInvalidChannel() {
        assertThrows(InvalidRequestException.class,
                () -> campaignService.create(request(
                        "Q4 Webinar", "TIKTOK", "PLANNED",
                        LocalDate.now(), LocalDate.now().plusDays(5))));
    }

    @Test
    void updateStatusNormalizesInputAndPersists() {
        Campaign stored = campaign(
                "campaign-1", "Q4 Webinar", "PLANNED", "EMAIL",
                LocalDate.now(), LocalDate.now().plusDays(5));
        when(campaignRepository.findById("campaign-1"))
                .thenReturn(Optional.of(stored));
        when(campaignRepository.save(any(Campaign.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        CampaignResponse response =
                campaignService.updateStatus("campaign-1", "paused");

        assertEquals("PAUSED", response.status());
        assertEquals("PAUSED", stored.getStatus());
    }

    @Test
    void updateStatusRejectsUnknownStatus() {
        when(campaignRepository.findById("campaign-1"))
                .thenReturn(Optional.of(campaign(
                        "campaign-1", "Q4 Webinar", "PLANNED", "EMAIL",
                        LocalDate.now(), LocalDate.now().plusDays(5))));

        assertThrows(InvalidRequestException.class,
                () -> campaignService.updateStatus("campaign-1", "RUNNING"));
    }

    @Test
    void listFiltersByStatusChannelAndSearch() {
        LocalDate today = LocalDate.now();
        Campaign email = campaign(
                "campaign-1", "Q4 Webinar", "ACTIVE", "EMAIL", today,
                today.plusDays(10));
        Campaign social = campaign(
                "campaign-2", "Launch Teaser", "PLANNED", "SOCIAL", today,
                today.plusDays(10));

        when(campaignRepository.findAllByOrderByCreatedAtDesc())
                .thenReturn(List.of(email, social));
        when(leadRepository.findByCampaignIdIn(anyCollection()))
                .thenReturn(List.of());

        PagedResult<CampaignResponse> result = campaignService.list(
                "webinar", "ACTIVE", "EMAIL", 1, 10);

        assertEquals(1, result.total());
        assertEquals("campaign-1", result.items().get(0).id());
    }

    @Test
    void responseDerivesLeadConversionAndValueMetrics() {
        LocalDate today = LocalDate.now();
        Campaign stored = campaign(
                "campaign-1", "Q4 Webinar", "ACTIVE", "EMAIL", today,
                today.plusDays(10));
        stored.setBudget(new BigDecimal("10000"));

        when(campaignRepository.findAllByOrderByCreatedAtDesc())
                .thenReturn(List.of(stored));
        when(leadRepository.findByCampaignIdIn(anyCollection()))
                .thenReturn(List.of(
                        lead("lead-1", "campaign-1", "WON", "client-1", "1000"),
                        lead("lead-2", "campaign-1", "WON", null, "500"),
                        lead("lead-3", "campaign-1", "NEW", null, "250")));

        CampaignResponse response = campaignService.list(
                null, null, null, 1, 10).items().get(0);

        assertEquals(3, response.leadCount());
        assertEquals(2, response.wonLeadCount());
        assertEquals(1, response.convertedLeadCount());
        assertEquals(33.33, response.conversionRate());
        assertEquals(new BigDecimal("1750"), response.pipelineValue());
        assertEquals(new BigDecimal("1500"), response.wonValue());
        assertTrue(response.activeNow());
    }

    @Test
    void activeNowIsFalseWhenOutsideDatesOrNotActive() {
        LocalDate today = LocalDate.now();
        Campaign future = campaign(
                "campaign-1", "Future", "ACTIVE", "EMAIL",
                today.plusDays(2), today.plusDays(10));
        when(campaignRepository.findAllByOrderByCreatedAtDesc())
                .thenReturn(List.of(future));
        when(leadRepository.findByCampaignIdIn(anyCollection()))
                .thenReturn(List.of());

        assertFalse(campaignService.list(null, null, null, 1, 10)
                .items().get(0).activeNow());
    }

    @Test
    void deleteProtectsAttributedLeadsByDefault() {
        Campaign stored = campaign(
                "campaign-1", "Q4 Webinar", "ACTIVE", "EMAIL",
                LocalDate.now(), LocalDate.now().plusDays(10));
        when(campaignRepository.findById("campaign-1"))
                .thenReturn(Optional.of(stored));
        when(leadRepository.findByCampaignId("campaign-1"))
                .thenReturn(List.of(lead(
                        "lead-1", "campaign-1", "NEW", null, "1000")));

        assertThrows(ConflictException.class,
                () -> campaignService.delete("campaign-1", false));

        verify(campaignRepository, never()).delete(any(Campaign.class));
    }

    @Test
    void deleteWithDetachClearsCampaignOnLeads() {
        Campaign stored = campaign(
                "campaign-1", "Q4 Webinar", "ACTIVE", "EMAIL",
                LocalDate.now(), LocalDate.now().plusDays(10));
        Lead attributed = lead("lead-1", "campaign-1", "NEW", null, "1000");
        when(campaignRepository.findById("campaign-1"))
                .thenReturn(Optional.of(stored));
        when(leadRepository.findByCampaignId("campaign-1"))
                .thenReturn(List.of(attributed));

        campaignService.delete("campaign-1", true);

        ArgumentCaptor<List<Lead>> captor =
                ArgumentCaptor.forClass(List.class);
        verify(leadRepository).saveAll(captor.capture());
        assertNull(captor.getValue().get(0).getCampaignId());
        verify(campaignRepository).delete(stored);
    }
}
