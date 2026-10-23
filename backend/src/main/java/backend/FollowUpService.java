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
import java.util.Set;

import org.springframework.stereotype.Service;

/**
 * Business rules for follow-up activities.
 *
 * Responsibilities:
 *  - require a supported activity type and a due date;
 *  - require exactly one lead OR client link and validate that it exists;
 *  - validate the optional assigned employee reference;
 *  - resolve lead/client/employee display names at read time;
 *  - compute the overdue flag server-side (due before today and not done).
 */
@Service
public class FollowUpService {

    public static final Set<String> ALLOWED_ACTIVITY_TYPES =
            Set.of("CALL", "EMAIL", "MEETING", "DEMO", "REMINDER", "OTHER");

    private final FollowUpRepository followUpRepository;
    private final LeadRepository leadRepository;
    private final ClientRepository clientRepository;
    private final EmployeeRepository employeeRepository;

    public FollowUpService(
            FollowUpRepository followUpRepository,
            LeadRepository leadRepository,
            ClientRepository clientRepository,
            EmployeeRepository employeeRepository) {
        this.followUpRepository = followUpRepository;
        this.leadRepository = leadRepository;
        this.clientRepository = clientRepository;
        this.employeeRepository = employeeRepository;
    }

    // ----- queries ------------------------------------------------------

    public PagedResult<FollowUpResponse> list(
            String search,
            String leadId,
            String clientId,
            String assignedToId,
            String completed,
            boolean overdueOnly,
            int page,
            int size) {

        String leadFilter = blankToNull(leadId);
        String clientFilter = blankToNull(clientId);
        String assigneeFilter = blankToNull(assignedToId);
        Boolean completedFilter = parseCompleted(completed);
        String needle = search == null ? null
                : search.trim().toLowerCase(Locale.ROOT);
        LocalDate today = LocalDate.now();

        List<FollowUp> filtered = followUpRepository
                .findAllByOrderByCreatedAtDesc()
                .stream()
                .filter(item -> leadFilter == null
                        || leadFilter.equals(item.getLeadId()))
                .filter(item -> clientFilter == null
                        || clientFilter.equals(item.getClientId()))
                .filter(item -> assigneeFilter == null
                        || assigneeFilter.equals(item.getAssignedToId()))
                .filter(item -> completedFilter == null
                        || completedFilter == item.isCompleted())
                .filter(item -> !overdueOnly || isOverdue(item, today))
                .filter(item -> needle == null || needle.isEmpty()
                        || matches(item, needle))
                .toList();

        return PagedResult.of(buildResponses(filtered), page, size);
    }

    public List<FollowUpResponse> listByLead(String leadId) {
        if (leadRepository.findById(leadId).isEmpty()) {
            throw new ResourceNotFoundException("Lead not found: " + leadId);
        }
        return buildResponses(followUpRepository.findByLeadId(leadId));
    }

    public List<FollowUpResponse> listByClient(String clientId) {
        if (clientRepository.findById(clientId).isEmpty()) {
            throw new ResourceNotFoundException("Client not found: " + clientId);
        }
        return buildResponses(followUpRepository.findByClientId(clientId));
    }

    public FollowUp getEntity(String id) {
        return followUpRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Follow-up not found: " + id));
    }

    public FollowUpResponse getById(String id) {
        return buildResponses(List.of(getEntity(id))).get(0);
    }

    // ----- mutations ----------------------------------------------------

    public FollowUpResponse create(FollowUpRequest request) {
        FollowUp followUp = new FollowUp();
        followUp.setCreatedAt(Instant.now());
        apply(followUp, request);
        followUp.setUpdatedAt(Instant.now());

        FollowUp saved = followUpRepository.save(followUp);
        return buildResponses(List.of(saved)).get(0);
    }

    public FollowUpResponse update(String id, FollowUpRequest request) {
        FollowUp existing = getEntity(id);

        apply(existing, request);
        // createdAt is preserved.
        existing.setUpdatedAt(Instant.now());

        FollowUp saved = followUpRepository.save(existing);
        return buildResponses(List.of(saved)).get(0);
    }

    public void delete(String id) {
        FollowUp followUp = getEntity(id);
        followUpRepository.delete(followUp);
    }

    // ----- helpers ------------------------------------------------------

    private void apply(FollowUp followUp, FollowUpRequest request) {
        followUp.setActivityType(normalizeActivityType(request.activityType()));

        String leadId = blankToNull(request.leadId());
        String clientId = blankToNull(request.clientId());

        if ((leadId == null) == (clientId == null)) {
            throw new InvalidRequestException(
                    "A follow-up must be linked to exactly one lead or client");
        }

        if (leadId != null && leadRepository.findById(leadId).isEmpty()) {
            throw new InvalidRequestException(
                    "Follow-up references an unknown lead: " + leadId);
        }
        if (clientId != null && clientRepository.findById(clientId).isEmpty()) {
            throw new InvalidRequestException(
                    "Follow-up references an unknown client: " + clientId);
        }

        String assignedToId = blankToNull(request.assignedToId());
        if (assignedToId != null
                && employeeRepository.findById(assignedToId).isEmpty()) {
            throw new InvalidRequestException(
                    "Follow-up assignee references an unknown employee: "
                            + assignedToId);
        }

        followUp.setDueDate(request.dueDate());
        followUp.setNotes(blankToNull(request.notes()));
        followUp.setCompleted(Boolean.TRUE.equals(request.completed()));
        followUp.setAssignedToId(assignedToId);
        followUp.setLeadId(leadId);
        followUp.setClientId(clientId);
    }

    private List<FollowUpResponse> buildResponses(List<FollowUp> followUps) {
        Map<String, Employee> employees = resolveEmployees(followUps);
        Map<String, Lead> leads = resolveLeads(followUps);
        Map<String, Client> clients = resolveClients(followUps);
        LocalDate today = LocalDate.now();

        List<FollowUpResponse> responses = new ArrayList<>(followUps.size());
        for (FollowUp followUp : followUps) {
            responses.add(toResponse(followUp, employees, leads, clients, today));
        }
        return responses;
    }

    private FollowUpResponse toResponse(
            FollowUp followUp,
            Map<String, Employee> employees,
            Map<String, Lead> leads,
            Map<String, Client> clients,
            LocalDate today) {

        Lead lead = lookup(leads, followUp.getLeadId());
        Client client = lookup(clients, followUp.getClientId());

        return new FollowUpResponse(
                followUp.getId(),
                followUp.getActivityType(),
                followUp.getDueDate(),
                followUp.getNotes(),
                followUp.isCompleted(),
                followUp.getAssignedToId(),
                EmployeeRef.from(lookup(employees, followUp.getAssignedToId())),
                followUp.getLeadId(),
                lead == null ? null : lead.getName(),
                followUp.getClientId(),
                client == null ? null : client.getName(),
                isOverdue(followUp, today),
                followUp.getCreatedAt(),
                followUp.getUpdatedAt());
    }

    private Map<String, Employee> resolveEmployees(List<FollowUp> followUps) {
        Set<String> ids = new LinkedHashSet<>();
        for (FollowUp followUp : followUps) {
            if (followUp.getAssignedToId() != null) {
                ids.add(followUp.getAssignedToId());
            }
        }
        return findEmployees(ids);
    }

    private Map<String, Lead> resolveLeads(List<FollowUp> followUps) {
        Set<String> ids = collect(followUps, true);
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<String, Lead> map = new HashMap<>();
        for (Lead lead : leadRepository.findAllById(ids)) {
            map.put(lead.getId(), lead);
        }
        return map;
    }

    private Map<String, Client> resolveClients(List<FollowUp> followUps) {
        Set<String> ids = collect(followUps, false);
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<String, Client> map = new HashMap<>();
        for (Client client : clientRepository.findAllById(ids)) {
            map.put(client.getId(), client);
        }
        return map;
    }

    private Set<String> collect(List<FollowUp> followUps, boolean leads) {
        Set<String> ids = new LinkedHashSet<>();
        for (FollowUp followUp : followUps) {
            String id = leads ? followUp.getLeadId() : followUp.getClientId();
            if (id != null) {
                ids.add(id);
            }
        }
        return ids;
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

    private boolean matches(FollowUp followUp, String needle) {
        return contains(followUp.getActivityType(), needle)
                || contains(followUp.getNotes(), needle);
    }

    private boolean contains(String value, String needle) {
        return value != null
                && value.toLowerCase(Locale.ROOT).contains(needle);
    }

    private static String normalizeActivityType(String value) {
        if (value == null || value.isBlank()) {
            throw new InvalidRequestException(
                    "Activity type must be one of "
                            + String.join(", ", ALLOWED_ACTIVITY_TYPES));
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (!ALLOWED_ACTIVITY_TYPES.contains(normalized)) {
            throw new InvalidRequestException(
                    "Activity type must be one of "
                            + String.join(", ", ALLOWED_ACTIVITY_TYPES));
        }
        return normalized;
    }

    private static Boolean parseCompleted(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if ("true".equals(normalized)) {
            return Boolean.TRUE;
        }
        if ("false".equals(normalized)) {
            return Boolean.FALSE;
        }
        throw new InvalidRequestException("Completed must be true or false");
    }

    private static boolean isOverdue(FollowUp followUp, LocalDate today) {
        return followUp.getDueDate() != null
                && followUp.getDueDate().isBefore(today)
                && !followUp.isCompleted();
    }

    private static <T> T lookup(Map<String, T> map, String key) {
        return key == null ? null : map.get(key);
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
