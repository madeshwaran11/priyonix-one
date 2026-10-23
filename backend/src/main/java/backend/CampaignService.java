package backend;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Service;

/**
 * Business rules for marketing campaigns.
 *
 * Responsibilities:
 *  - enforce the allowed lifecycle status and channel values;
 *  - validate the campaign start/end date ordering;
 *  - prevent duplicate campaign names;
 *  - derive performance metrics (leads, conversions, pipeline value) from the
 *    persisted leads attributed to each campaign;
 *  - protect attributed leads when a campaign is deleted, or detach them when
 *    the caller explicitly asks for it.
 */
@Service
public class CampaignService {

    public static final Set<String> ALLOWED_STATUSES = Set.of(
            "PLANNED", "ACTIVE", "PAUSED", "COMPLETED", "CANCELLED");

    public static final Set<String> ALLOWED_CHANNELS = Set.of(
            "EMAIL", "SOCIAL", "SEARCH", "EVENTS", "CONTENT", "REFERRAL",
            "PAID_ADS", "OTHER");

    public static final String DEFAULT_STATUS = "PLANNED";
    public static final String DEFAULT_CHANNEL = "OTHER";

    private final CampaignRepository campaignRepository;
    private final LeadRepository leadRepository;

    public CampaignService(
            CampaignRepository campaignRepository,
            LeadRepository leadRepository) {
        this.campaignRepository = campaignRepository;
        this.leadRepository = leadRepository;
    }

    // ----- queries ------------------------------------------------------

    public PagedResult<CampaignResponse> list(
            String search,
            String status,
            String channel,
            int page,
            int size) {

        String statusFilter = status == null || status.isBlank()
                ? null : requireEnum(status, ALLOWED_STATUSES, "Status");
        String channelFilter = channel == null || channel.isBlank()
                ? null : requireEnum(channel, ALLOWED_CHANNELS, "Channel");
        String needle = search == null ? null
                : search.trim().toLowerCase(Locale.ROOT);

        List<Campaign> filtered = campaignRepository
                .findAllByOrderByCreatedAtDesc()
                .stream()
                .filter(campaign -> statusFilter == null
                        || statusFilter.equalsIgnoreCase(campaign.getStatus()))
                .filter(campaign -> channelFilter == null
                        || channelFilter.equalsIgnoreCase(campaign.getChannel()))
                .filter(campaign -> needle == null || needle.isEmpty()
                        || matches(campaign, needle))
                .toList();

        return PagedResult.of(buildResponses(filtered), page, size);
    }

    public Campaign getEntity(String id) {
        return campaignRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Campaign not found: " + id));
    }

    public CampaignResponse getById(String id) {
        return buildResponses(List.of(getEntity(id))).get(0);
    }

    // ----- mutations ----------------------------------------------------

    public CampaignResponse create(CampaignRequest request) {
        ensureUniqueName(request.name(), null);

        Campaign campaign = new Campaign();
        campaign.setCreatedAt(Instant.now());
        apply(campaign, request);
        campaign.setUpdatedAt(Instant.now());

        Campaign saved = campaignRepository.save(campaign);
        return buildResponses(List.of(saved)).get(0);
    }

    public CampaignResponse update(String id, CampaignRequest request) {
        Campaign existing = getEntity(id);

        ensureUniqueName(request.name(), id);
        apply(existing, request);
        existing.setUpdatedAt(Instant.now());

        Campaign saved = campaignRepository.save(existing);
        return buildResponses(List.of(saved)).get(0);
    }

    public CampaignResponse updateStatus(String id, String status) {
        Campaign campaign = getEntity(id);

        campaign.setStatus(requireEnum(status, ALLOWED_STATUSES, "Status"));
        campaign.setUpdatedAt(Instant.now());

        Campaign saved = campaignRepository.save(campaign);
        return buildResponses(List.of(saved)).get(0);
    }

    /**
     * Deletes a campaign. Attributed leads are protected unless
     * {@code detachLeads} is true, in which case their campaignId is cleared
     * so the lead records and their conversion history survive.
     */
    public void delete(String id, boolean detachLeads) {
        Campaign campaign = getEntity(id);
        List<Lead> leads = leadRepository.findByCampaignId(id);

        if (!leads.isEmpty() && !detachLeads) {
            throw new ConflictException(
                    "Campaign has " + leads.size() + " attributed lead(s). "
                            + "Retry with ?detachLeads=true to delete the "
                            + "campaign and detach those leads");
        }

        if (detachLeads && !leads.isEmpty()) {
            Instant now = Instant.now();
            for (Lead lead : leads) {
                lead.setCampaignId(null);
                lead.setUpdatedAt(now);
            }
            leadRepository.saveAll(leads);
        }

        campaignRepository.delete(campaign);
    }

    // ----- helpers ------------------------------------------------------

    private void apply(Campaign campaign, CampaignRequest request) {
        validateDates(request.startDate(), request.endDate());

        campaign.setName(request.name());
        campaign.setDescription(blankToNull(request.description()));
        campaign.setChannel(normalizeEnum(
                request.channel(), ALLOWED_CHANNELS, DEFAULT_CHANNEL,
                "Channel"));
        campaign.setStatus(normalizeEnum(
                request.status(), ALLOWED_STATUSES, DEFAULT_STATUS, "Status"));
        campaign.setStartDate(request.startDate());
        campaign.setEndDate(request.endDate());
        campaign.setBudget(request.budget());
        campaign.setSpend(request.spend());
    }

    private void validateDates(LocalDate startDate, LocalDate endDate) {
        if (startDate != null && endDate != null
                && endDate.isBefore(startDate)) {
            throw new InvalidRequestException(
                    "Campaign end date must be on or after the start date");
        }
    }

    private void ensureUniqueName(String name, String currentId) {
        if (name == null || name.isBlank()) {
            return;
        }

        Optional<Campaign> existing =
                campaignRepository.findByNameIgnoreCase(name.trim());
        if (existing.isPresent()
                && (currentId == null
                        || !existing.get().getId().equals(currentId))) {
            throw new DuplicateResourceException(
                    "A campaign named " + name.trim() + " already exists");
        }
    }

    private List<CampaignResponse> buildResponses(List<Campaign> campaigns) {
        Map<String, List<Lead>> leadsByCampaign = leadsByCampaign(campaigns);
        LocalDate today = LocalDate.now();

        List<CampaignResponse> responses = new ArrayList<>(campaigns.size());
        for (Campaign campaign : campaigns) {
            responses.add(toResponse(
                    campaign,
                    leadsByCampaign.getOrDefault(campaign.getId(), List.of()),
                    today));
        }
        return responses;
    }

    private CampaignResponse toResponse(
            Campaign campaign, List<Lead> leads, LocalDate today) {

        int won = 0;
        int converted = 0;
        BigDecimal pipelineValue = BigDecimal.ZERO;
        BigDecimal wonValue = BigDecimal.ZERO;

        for (Lead lead : leads) {
            if ("WON".equalsIgnoreCase(lead.getStatus())) {
                won++;
                if (lead.getEstimatedValue() != null) {
                    wonValue = wonValue.add(lead.getEstimatedValue());
                }
            }
            if (lead.getConvertedClientId() != null) {
                converted++;
            }
            if (lead.getEstimatedValue() != null) {
                pipelineValue = pipelineValue.add(lead.getEstimatedValue());
            }
        }

        int leadCount = leads.size();
        double conversionRate = leadCount == 0 ? 0.0
                : BigDecimal.valueOf(converted * 100.0 / leadCount)
                        .setScale(2, RoundingMode.HALF_UP)
                        .doubleValue();

        boolean activeNow = "ACTIVE".equalsIgnoreCase(campaign.getStatus())
                && campaign.getStartDate() != null
                && campaign.getEndDate() != null
                && !today.isBefore(campaign.getStartDate())
                && !today.isAfter(campaign.getEndDate());

        return new CampaignResponse(
                campaign.getId(),
                campaign.getName(),
                campaign.getDescription(),
                campaign.getChannel(),
                campaign.getStatus(),
                campaign.getStartDate(),
                campaign.getEndDate(),
                campaign.getBudget(),
                campaign.getSpend(),
                activeNow,
                leadCount,
                won,
                converted,
                conversionRate,
                pipelineValue,
                wonValue,
                campaign.getCreatedAt(),
                campaign.getUpdatedAt());
    }

    private Map<String, List<Lead>> leadsByCampaign(List<Campaign> campaigns) {
        Set<String> ids = new LinkedHashSet<>();
        for (Campaign campaign : campaigns) {
            if (campaign.getId() != null) {
                ids.add(campaign.getId());
            }
        }
        if (ids.isEmpty()) {
            return Map.of();
        }

        Map<String, List<Lead>> grouped = new HashMap<>();
        for (Lead lead : leadRepository.findByCampaignIdIn(ids)) {
            if (lead.getCampaignId() != null) {
                grouped.computeIfAbsent(
                        lead.getCampaignId(), key -> new ArrayList<>()).add(lead);
            }
        }
        return grouped;
    }

    private boolean matches(Campaign campaign, String needle) {
        return contains(campaign.getName(), needle)
                || contains(campaign.getDescription(), needle)
                || contains(campaign.getChannel(), needle)
                || contains(campaign.getStatus(), needle);
    }

    private boolean contains(String value, String needle) {
        return value != null
                && value.toLowerCase(Locale.ROOT).contains(needle);
    }

    private static String normalizeEnum(
            String value, Set<String> allowed, String defaultValue,
            String fieldName) {

        if (value == null || value.isBlank()) {
            return defaultValue;
        }

        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (!allowed.contains(normalized)) {
            throw new InvalidRequestException(
                    fieldName + " must be one of "
                            + String.join(", ", allowed));
        }
        return normalized;
    }

    private static String requireEnum(
            String value, Set<String> allowed, String fieldName) {
        String normalized = value == null ? ""
                : value.trim().toUpperCase(Locale.ROOT);
        if (!allowed.contains(normalized)) {
            throw new InvalidRequestException(
                    fieldName + " must be one of "
                            + String.join(", ", allowed));
        }
        return normalized;
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
