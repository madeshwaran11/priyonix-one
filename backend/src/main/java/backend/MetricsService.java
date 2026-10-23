package backend;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.stereotype.Service;

/**
 * Aggregates real project/task/client/lead/follow-up figures for the dashboard
 * overview.
 *
 * Every value is a live count from the corresponding collection — nothing here
 * is synthesised. "Overdue" means a due date before today that is not
 * completed (a task that is not COMPLETED, or a follow-up that is not done).
 */
@Service
public class MetricsService {

    private final ProjectRepository projectRepository;
    private final TaskRepository taskRepository;
    private final ClientRepository clientRepository;
    private final LeadRepository leadRepository;
    private final FollowUpRepository followUpRepository;
    private final CampaignRepository campaignRepository;

    public MetricsService(
            ProjectRepository projectRepository,
            TaskRepository taskRepository,
            ClientRepository clientRepository,
            LeadRepository leadRepository,
            FollowUpRepository followUpRepository,
            CampaignRepository campaignRepository) {
        this.projectRepository = projectRepository;
        this.taskRepository = taskRepository;
        this.clientRepository = clientRepository;
        this.leadRepository = leadRepository;
        this.followUpRepository = followUpRepository;
        this.campaignRepository = campaignRepository;
    }

    public Map<String, Object> metrics() {
        LocalDate today = LocalDate.now();

        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("totalProjects", projectRepository.count());
        metrics.put("activeProjects",
                projectRepository.countByStatusIgnoreCase("ACTIVE"));
        metrics.put("completedProjects",
                projectRepository.countByStatusIgnoreCase("COMPLETED"));
        metrics.put("totalTasks", taskRepository.count());
        metrics.put("tasksInProgress",
                taskRepository.countByStatusIgnoreCase("IN_PROGRESS"));
        metrics.put("overdueTasks",
                taskRepository.countByDueDateBeforeAndStatusNot(
                        today, "COMPLETED"));

        metrics.put("totalClients", clientRepository.count());
        metrics.put("activeClients",
                clientRepository.countByStatusIgnoreCase("ACTIVE"));
        metrics.put("totalLeads", leadRepository.count());
        metrics.put("wonLeads", leadRepository.countByStatusIgnoreCase("WON"));
        metrics.put("openFollowUps", followUpRepository.countByCompletedFalse());
        metrics.put("overdueFollowUps",
                followUpRepository.countByCompletedFalseAndDueDateBefore(
                        today));

        long attributedLeads = leadRepository.countByCampaignIdNotNull();
        long campaignConversions =
                leadRepository.countByCampaignIdNotNullAndConvertedClientIdNotNull();
        BigDecimal campaignBudget = BigDecimal.ZERO;
        BigDecimal campaignSpend = BigDecimal.ZERO;
        for (Campaign campaign : campaignRepository.findAll()) {
            if (campaign.getBudget() != null) {
                campaignBudget = campaignBudget.add(campaign.getBudget());
            }
            if (campaign.getSpend() != null) {
                campaignSpend = campaignSpend.add(campaign.getSpend());
            }
        }

        metrics.put("totalCampaigns", campaignRepository.count());
        metrics.put("activeCampaigns",
                campaignRepository.countByStatusIgnoreCase("ACTIVE"));
        metrics.put("attributedLeads", attributedLeads);
        metrics.put("campaignConversions", campaignConversions);
        metrics.put("campaignConversionRate",
                attributedLeads == 0 ? 0.0
                        : BigDecimal
                                .valueOf(campaignConversions * 100.0
                                        / attributedLeads)
                                .setScale(2, RoundingMode.HALF_UP)
                                .doubleValue());
        metrics.put("campaignBudgetTotal", campaignBudget);
        metrics.put("campaignSpendTotal", campaignSpend);

        return metrics;
    }
}
