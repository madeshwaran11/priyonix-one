package backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MetricsServiceTest {

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private TaskRepository taskRepository;

    @Mock
    private ClientRepository clientRepository;

    @Mock
    private LeadRepository leadRepository;

    @Mock
    private FollowUpRepository followUpRepository;

    @Mock
    private CampaignRepository campaignRepository;

    private MetricsService metricsService;

    @BeforeEach
    void setUp() {
        metricsService = new MetricsService(
                projectRepository,
                taskRepository,
                clientRepository,
                leadRepository,
                followUpRepository,
                campaignRepository);
    }

    @Test
    void exposesLiveMarketingMetrics() {
        Campaign email = new Campaign();
        email.setBudget(new BigDecimal("1000"));
        email.setSpend(new BigDecimal("500"));

        Campaign social = new Campaign();
        social.setBudget(new BigDecimal("2000"));
        social.setSpend(new BigDecimal("1500"));

        when(campaignRepository.count()).thenReturn(2L);
        when(campaignRepository.countByStatusIgnoreCase("ACTIVE"))
                .thenReturn(1L);
        when(campaignRepository.findAll()).thenReturn(List.of(email, social));
        when(leadRepository.countByCampaignIdNotNull()).thenReturn(4L);
        when(leadRepository
                .countByCampaignIdNotNullAndConvertedClientIdNotNull())
                .thenReturn(2L);

        Map<String, Object> metrics = metricsService.metrics();

        assertEquals(2L, metrics.get("totalCampaigns"));
        assertEquals(1L, metrics.get("activeCampaigns"));
        assertEquals(4L, metrics.get("attributedLeads"));
        assertEquals(2L, metrics.get("campaignConversions"));
        assertEquals(50.0, metrics.get("campaignConversionRate"));
        assertEquals(new BigDecimal("3000"),
                metrics.get("campaignBudgetTotal"));
        assertEquals(new BigDecimal("2000"),
                metrics.get("campaignSpendTotal"));
    }

    @Test
    void marketingConversionRateIsZeroWithoutAttributedLeads() {
        when(campaignRepository.findAll()).thenReturn(List.of());
        when(leadRepository.countByCampaignIdNotNull()).thenReturn(0L);
        when(leadRepository
                .countByCampaignIdNotNullAndConvertedClientIdNotNull())
                .thenReturn(0L);

        Map<String, Object> metrics = metricsService.metrics();

        assertEquals(0.0, metrics.get("campaignConversionRate"));
        assertEquals(BigDecimal.ZERO, metrics.get("campaignBudgetTotal"));
        assertEquals(BigDecimal.ZERO, metrics.get("campaignSpendTotal"));
    }
}
