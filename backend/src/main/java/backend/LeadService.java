package backend;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Service;

/**
 * Business rules for CRM leads.
 *
 * Responsibilities:
 *  - validate that the assigned employee references an existing record;
 *  - enforce the allowed status (pipeline stage) and priority values;
 *  - resolve employee display names, the converted client name and live
 *    follow-up counts at read time;
 *  - convert a WON lead into a client exactly once, reusing an existing client
 *    with the same email instead of creating a duplicate;
 *  - protect leads that still have follow-ups from accidental deletion.
 */
@Service
public class LeadService {
    @org.springframework.beans.factory.annotation.Autowired
    private LegacyIntegrityService integrity;


    public static final Set<String> ALLOWED_STATUSES =
            Set.of("NEW", "CONTACTED", "QUALIFIED", "PROPOSAL", "WON", "LOST");

    public static final Set<String> ALLOWED_PRIORITIES =
            Set.of("LOW", "MEDIUM", "HIGH", "CRITICAL");

    public static final String DEFAULT_STATUS = "NEW";
    public static final String DEFAULT_PRIORITY = "MEDIUM";
    public static final String WON = "WON";

    private final LeadRepository leadRepository;
    private final ClientRepository clientRepository;
    private final EmployeeRepository employeeRepository;
    private final FollowUpRepository followUpRepository;
    private final CampaignRepository campaignRepository;
    private final ClientService clientService;

    public LeadService(
            LeadRepository leadRepository,
            ClientRepository clientRepository,
            EmployeeRepository employeeRepository,
            FollowUpRepository followUpRepository,
            CampaignRepository campaignRepository,
            ClientService clientService) {
        this.leadRepository = leadRepository;
        this.clientRepository = clientRepository;
        this.employeeRepository = employeeRepository;
        this.followUpRepository = followUpRepository;
        this.campaignRepository = campaignRepository;
        this.clientService = clientService;
    }

    // ----- queries ------------------------------------------------------

    public PagedResult<LeadResponse> list(
            String search,
            String status,
            String priority,
            String assignedToId,
            String campaignId,
            int page,
            int size) {

        String statusFilter = status == null || status.isBlank()
                ? null : requireEnum(status, ALLOWED_STATUSES, "Status");
        String priorityFilter = priority == null || priority.isBlank()
                ? null : requireEnum(priority, ALLOWED_PRIORITIES, "Priority");
        String assigneeFilter = blankToNull(assignedToId);
        String campaignFilter = blankToNull(campaignId);
        String needle = search == null ? null
                : search.trim().toLowerCase(Locale.ROOT);

        List<Lead> filtered = leadRepository
                .findAllByOrderByCreatedAtDesc()
                .stream()
                .filter(lead -> statusFilter == null
                        || statusFilter.equalsIgnoreCase(lead.getStatus()))
                .filter(lead -> priorityFilter == null
                        || priorityFilter.equalsIgnoreCase(lead.getPriority()))
                .filter(lead -> assigneeFilter == null
                        || assigneeFilter.equals(lead.getAssignedToId()))
                .filter(lead -> campaignFilter == null
                        || campaignFilter.equals(lead.getCampaignId()))
                .filter(lead -> needle == null || needle.isEmpty()
                        || matches(lead, needle))
                .toList();

        return PagedResult.of(buildResponses(filtered), page, size);
    }

    public Lead getEntity(String id) {
        return leadRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Lead not found: " + id));
    }

    public LeadResponse getById(String id) {
        return buildResponses(List.of(getEntity(id))).get(0);
    }

    // ----- mutations ----------------------------------------------------

    public LeadResponse create(LeadRequest request) {
        Lead lead = new Lead();
        lead.setCreatedAt(Instant.now());
        apply(lead, request);
        lead.setUpdatedAt(Instant.now());

        Lead saved = leadRepository.save(lead);
        return buildResponses(List.of(saved)).get(0);
    }

    public LeadResponse update(String id, LeadRequest request) {
        Lead existing = getEntity(id);

        apply(existing, request);
        // createdAt, convertedClientId and convertedAt are preserved.
        existing.setUpdatedAt(Instant.now());

        Lead saved = leadRepository.save(existing);
        return buildResponses(List.of(saved)).get(0);
    }

    /**
     * Deletes a lead. A lead that still has follow-ups is protected unless
     * {@code cascade} is true, in which case those follow-ups are removed
     * first. Callers must be administrators (see SecurityConfig).
     */
    public void delete(String id, boolean cascade) {
        if (integrity != null) integrity.requireUnreferenced("leads",id);
        Lead lead = getEntity(id);
        long followUps = followUpRepository.findByLeadId(id).size();

        if (followUps > 0 && !cascade) {
            throw new ConflictException(
                    "Lead has " + followUps + " follow-up(s). Delete them or "
                            + "retry with ?cascade=true to remove the lead "
                            + "and its follow-ups");
        }

        if (cascade && followUps > 0) {
            followUpRepository.deleteByLeadId(id);
        }

        leadRepository.delete(lead);
    }

    /**
     * Converts a WON lead into a client.
     *
     * Guards against duplicate conversions: a lead can only be converted once
     * (tracked by convertedClientId) and an existing client with the same
     * email is reused rather than duplicated.
     */
    public LeadConversionResponse convert(String id) {
        Lead lead = getEntity(id);

        if (lead.getConvertedClientId() != null) {
            throw new ConflictException(
                    "Lead " + lead.getName() + " has already been converted "
                            + "to a client");
        }

        if (!WON.equalsIgnoreCase(lead.getStatus())) {
            throw new InvalidRequestException(
                    "Only a WON lead can be converted to a client");
        }

        String email = ClientService.normalizeEmail(lead.getEmail());
        if (email == null) {
            throw new InvalidRequestException(
                    "Lead must have an email before it can be converted");
        }

        ClientResponse clientResponse;
        boolean created;

        Optional<Client> existing =
                clientRepository.findByEmailIgnoreCase(email);
        if (existing.isPresent()) {
            clientResponse = clientService.toResponse(existing.get());
            created = false;
        } else {
            clientResponse = clientService.createFromLead(lead);
            created = true;
        }

        lead.setConvertedClientId(clientResponse.id());
        lead.setConvertedAt(Instant.now());
        lead.setUpdatedAt(Instant.now());
        Lead saved = leadRepository.save(lead);

        return new LeadConversionResponse(
                buildResponses(List.of(saved)).get(0), clientResponse, created);
    }

    // ----- helpers ------------------------------------------------------

    private void apply(Lead lead, LeadRequest request) {
        lead.setName(request.name());
        lead.setCompany(blankToNull(request.company()));
        lead.setEmail(ClientService.normalizeEmail(request.email()));
        lead.setPhone(blankToNull(request.phone()));
        lead.setSource(blankToNull(request.source()));
        lead.setStatus(normalizeEnum(
                request.status(), ALLOWED_STATUSES, DEFAULT_STATUS, "Status"));
        lead.setPriority(normalizeEnum(
                request.priority(), ALLOWED_PRIORITIES, DEFAULT_PRIORITY,
                "Priority"));
        lead.setAssignedToId(validateAssignee(request.assignedToId()));
        lead.setCampaignId(validateCampaign(request.campaignId()));
        lead.setEstimatedValue(request.estimatedValue());
        lead.setFollowUpDate(request.followUpDate());
        lead.setNotes(blankToNull(request.notes()));
    }

    private String validateAssignee(String assignedToId) {
        String id = blankToNull(assignedToId);
        if (id != null && employeeRepository.findById(id).isEmpty()) {
            throw new InvalidRequestException(
                    "Lead assignee references an unknown employee: " + id);
        }
        return id;
    }

    private String validateCampaign(String campaignId) {
        String id = blankToNull(campaignId);
        if (id != null && campaignRepository.findById(id).isEmpty()) {
            throw new InvalidRequestException(
                    "Lead campaign references an unknown campaign: " + id);
        }
        return id;
    }

    private List<LeadResponse> buildResponses(List<Lead> leads) {
        Map<String, Employee> employees = resolveEmployees(leads);
        Map<String, Client> convertedClients = resolveConvertedClients(leads);
        Map<String, Campaign> campaigns = resolveCampaigns(leads);
        Map<String, int[]> stats = followUpStatsByLead(leads);
        LocalDate today = LocalDate.now();

        List<LeadResponse> responses = new ArrayList<>(leads.size());
        for (Lead lead : leads) {
            responses.add(toResponse(
                    lead, employees, convertedClients, campaigns, stats, today));
        }
        return responses;
    }

    private LeadResponse toResponse(
            Lead lead,
            Map<String, Employee> employees,
            Map<String, Client> convertedClients,
            Map<String, Campaign> campaigns,
            Map<String, int[]> stats,
            LocalDate today) {

        int[] counts = stats.getOrDefault(lead.getId(), EMPTY_COUNTS);
        Client convertedClient = lookup(convertedClients, lead.getConvertedClientId());
        Campaign campaign = lookup(campaigns, lead.getCampaignId());

        return new LeadResponse(
                lead.getId(),
                lead.getName(),
                lead.getCompany(),
                lead.getEmail(),
                lead.getPhone(),
                lead.getSource(),
                lead.getStatus(),
                lead.getPriority(),
                lead.getAssignedToId(),
                EmployeeRef.from(lookup(employees, lead.getAssignedToId())),
                lead.getCampaignId(),
                campaign == null ? null : campaign.getName(),
                lead.getEstimatedValue(),
                lead.getFollowUpDate(),
                lead.getNotes(),
                lead.getConvertedClientId(),
                convertedClient == null ? null : convertedClient.getName(),
                lead.getConvertedAt(),
                counts[0],
                counts[1],
                lead.getCreatedAt(),
                lead.getUpdatedAt());
    }

    private Map<String, Employee> resolveEmployees(List<Lead> leads) {
        Set<String> ids = new LinkedHashSet<>();
        for (Lead lead : leads) {
            if (lead.getAssignedToId() != null) {
                ids.add(lead.getAssignedToId());
            }
        }
        return findEmployees(ids);
    }

    private Map<String, Client> resolveConvertedClients(List<Lead> leads) {
        Set<String> ids = new LinkedHashSet<>();
        for (Lead lead : leads) {
            if (lead.getConvertedClientId() != null) {
                ids.add(lead.getConvertedClientId());
            }
        }
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<String, Client> map = new HashMap<>();
        for (Client client : clientRepository.findAllById(ids)) {
            map.put(client.getId(), client);
        }
        return map;
    }

    private Map<String, Campaign> resolveCampaigns(List<Lead> leads) {
        Set<String> ids = new LinkedHashSet<>();
        for (Lead lead : leads) {
            if (lead.getCampaignId() != null) {
                ids.add(lead.getCampaignId());
            }
        }
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<String, Campaign> map = new HashMap<>();
        for (Campaign campaign : campaignRepository.findAllById(ids)) {
            map.put(campaign.getId(), campaign);
        }
        return map;
    }

    private Map<String, Employee> findEmployees(Collection<String> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<String, Employee> map = new HashMap<>();
        for (Employee employee : employeeRepository.findAllById(ids)) {
            map.put(employee.getId(), employee);
        }
        return map;
    }

    /** [total, overdue] follow-up counts keyed by lead id. */
    private Map<String, int[]> followUpStatsByLead(List<Lead> leads) {
        Collection<String> ids = leads.stream()
                .map(Lead::getId)
                .filter(Objects::nonNull)
                .toList();

        if (ids.isEmpty()) {
            return Map.of();
        }

        Map<String, int[]> stats = new HashMap<>();
        LocalDate today = LocalDate.now();
        for (FollowUp followUp : followUpRepository.findByLeadIdIn(ids)) {
            if (followUp.getLeadId() == null) {
                continue;
            }
            int[] counts = stats.computeIfAbsent(
                    followUp.getLeadId(), key -> new int[2]);
            counts[0]++;
            if (ClientService.isOverdue(followUp, today)) {
                counts[1]++;
            }
        }
        return stats;
    }

    private boolean matches(Lead lead, String needle) {
        return contains(lead.getName(), needle)
                || contains(lead.getCompany(), needle)
                || contains(lead.getEmail(), needle)
                || contains(lead.getSource(), needle)
                || contains(lead.getNotes(), needle);
    }

    private boolean contains(String value, String needle) {
        return value != null
                && value.toLowerCase(Locale.ROOT).contains(needle);
    }

    private static final int[] EMPTY_COUNTS = new int[] {0, 0};

    private static <T> T lookup(Map<String, T> map, String key) {
        return key == null ? null : map.get(key);
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
        String normalized = value.trim().toUpperCase(Locale.ROOT);
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
