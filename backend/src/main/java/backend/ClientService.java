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
 * Business rules for clients.
 *
 * Responsibilities:
 *  - reject duplicate client emails (case-insensitive);
 *  - validate that the assigned employee references an existing record;
 *  - enforce the allowed status values;
 *  - resolve employee display names and follow-up counts at read time;
 *  - protect clients that still have follow-ups from accidental deletion.
 */
@Service
public class ClientService {
    @org.springframework.beans.factory.annotation.Autowired
    private LegacyIntegrityService integrity;


    public static final Set<String> ALLOWED_STATUSES =
            Set.of("ACTIVE", "PROSPECT", "INACTIVE", "CHURNED");

    public static final String DEFAULT_STATUS = "ACTIVE";

    private final ClientRepository clientRepository;
    private final EmployeeRepository employeeRepository;
    private final FollowUpRepository followUpRepository;

    public ClientService(
            ClientRepository clientRepository,
            EmployeeRepository employeeRepository,
            FollowUpRepository followUpRepository) {
        this.clientRepository = clientRepository;
        this.employeeRepository = employeeRepository;
        this.followUpRepository = followUpRepository;
    }

    // ----- queries ------------------------------------------------------

    public PagedResult<ClientResponse> list(
            String search, String status, int page, int size) {

        String statusFilter = status == null || status.isBlank()
                ? null : requireEnum(status, ALLOWED_STATUSES, "Status");
        String needle = search == null ? null
                : search.trim().toLowerCase(Locale.ROOT);

        List<Client> filtered = clientRepository
                .findAllByOrderByCreatedAtDesc()
                .stream()
                .filter(client -> statusFilter == null
                        || statusFilter.equalsIgnoreCase(client.getStatus()))
                .filter(client -> needle == null || needle.isEmpty()
                        || matches(client, needle))
                .toList();

        return PagedResult.of(buildResponses(filtered), page, size);
    }

    public Client getEntity(String id) {
        return clientRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Client not found: " + id));
    }

    public ClientResponse getById(String id) {
        return buildResponses(List.of(getEntity(id))).get(0);
    }

    /** Looks up a client by its unique (normalized) email. */
    public Optional<Client> findByEmail(String email) {
        if (email == null || email.isBlank()) {
            return Optional.empty();
        }
        return clientRepository.findByEmailIgnoreCase(normalizeEmail(email));
    }

    /** Builds the API representation for a single client with live counts. */
    public ClientResponse toResponse(Client client) {
        return buildResponses(List.of(client)).get(0);
    }

    // ----- mutations ----------------------------------------------------

    public ClientResponse create(ClientRequest request) {
        String email = normalizeEmail(request.email());

        if (clientRepository.existsByEmailIgnoreCase(email)) {
            throw new DuplicateResourceException(
                    "A client with email " + email + " already exists");
        }

        Client client = new Client();
        client.setCreatedAt(Instant.now());
        apply(client, request, email);
        client.setUpdatedAt(Instant.now());

        Client saved = clientRepository.save(client);
        return buildResponses(List.of(saved)).get(0);
    }

    public ClientResponse update(String id, ClientRequest request) {
        Client existing = getEntity(id);
        String email = normalizeEmail(request.email());

        if (!equalsIgnoreCase(existing.getEmail(), email)
                && clientRepository.existsByEmailIgnoreCase(email)) {
            throw new DuplicateResourceException(
                    "A client with email " + email + " already exists");
        }

        apply(existing, request, email);
        // createdAt and sourceLeadId are preserved.
        existing.setUpdatedAt(Instant.now());

        Client saved = clientRepository.save(existing);
        return buildResponses(List.of(saved)).get(0);
    }

    /**
     * Deletes a client. A client that still has follow-ups is protected
     * unless {@code cascade} is true, in which case those follow-ups are
     * removed first. Callers must be administrators (see SecurityConfig).
     */
    public void delete(String id, boolean cascade) {
        if (integrity != null) integrity.requireUnreferenced("clients",id);
        Client client = getEntity(id);
        long followUps = followUpRepository.findByClientId(id).size();

        if (followUps > 0 && !cascade) {
            throw new ConflictException(
                    "Client has " + followUps + " follow-up(s). Delete them or "
                            + "retry with ?cascade=true to remove the client "
                            + "and its follow-ups");
        }

        if (cascade && followUps > 0) {
            followUpRepository.deleteByClientId(id);
        }

        clientRepository.delete(client);
    }

    /**
     * Creates a brand new client from a converted lead. Uniqueness of the
     * email is the caller's responsibility — {@link LeadService} only calls
     * this after confirming no client with that email exists.
     */
    public ClientResponse createFromLead(Lead lead) {
        Client client = new Client();
        client.setName(lead.getName());
        client.setCompanyName(lead.getCompany());
        client.setEmail(normalizeEmail(lead.getEmail()));
        client.setPhone(lead.getPhone());
        client.setStatus(DEFAULT_STATUS);
        client.setAssignedToId(lead.getAssignedToId());
        client.setNotes(lead.getNotes());
        client.setSourceLeadId(lead.getId());
        client.setCreatedAt(Instant.now());
        client.setUpdatedAt(Instant.now());

        Client saved = clientRepository.save(client);
        return buildResponses(List.of(saved)).get(0);
    }

    // ----- helpers ------------------------------------------------------

    private void apply(Client client, ClientRequest request, String email) {
        client.setName(request.name());
        client.setCompanyName(blankToNull(request.companyName()));
        client.setEmail(email);
        client.setPhone(blankToNull(request.phone()));
        client.setAddress(blankToNull(request.address()));
        client.setIndustry(blankToNull(request.industry()));
        client.setStatus(normalizeEnum(
                request.status(), ALLOWED_STATUSES, DEFAULT_STATUS, "Status"));
        client.setAssignedToId(validateAssignee(request.assignedToId()));
        client.setNotes(blankToNull(request.notes()));
    }

    private String validateAssignee(String assignedToId) {
        String id = blankToNull(assignedToId);
        if (id != null && employeeRepository.findById(id).isEmpty()) {
            throw new InvalidRequestException(
                    "Client owner references an unknown employee: " + id);
        }
        return id;
    }

    private List<ClientResponse> buildResponses(List<Client> clients) {
        Map<String, Employee> employees = resolveEmployees(clients);
        Map<String, int[]> stats = followUpStatsByClient(clients);
        LocalDate today = LocalDate.now();

        List<ClientResponse> responses = new ArrayList<>(clients.size());
        for (Client client : clients) {
            responses.add(toResponse(client, employees, stats, today));
        }
        return responses;
    }

    private ClientResponse toResponse(
            Client client,
            Map<String, Employee> employees,
            Map<String, int[]> stats,
            LocalDate today) {

        int[] counts = stats.getOrDefault(client.getId(), EMPTY_COUNTS);

        return new ClientResponse(
                client.getId(),
                client.getName(),
                client.getCompanyName(),
                client.getEmail(),
                client.getPhone(),
                client.getAddress(),
                client.getIndustry(),
                client.getStatus(),
                client.getAssignedToId(),
                EmployeeRef.from(lookup(employees, client.getAssignedToId())),
                client.getNotes(),
                client.getSourceLeadId(),
                counts[0],
                counts[1],
                client.getCreatedAt(),
                client.getUpdatedAt());
    }

    private Map<String, Employee> resolveEmployees(List<Client> clients) {
        Set<String> ids = new LinkedHashSet<>();
        for (Client client : clients) {
            if (client.getAssignedToId() != null) {
                ids.add(client.getAssignedToId());
            }
        }
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<String, Employee> map = new HashMap<>();
        for (Employee employee : employeeRepository.findAllById(ids)) {
            map.put(employee.getId(), employee);
        }
        return map;
    }

    /** [total, overdue] follow-up counts keyed by client id. */
    private Map<String, int[]> followUpStatsByClient(List<Client> clients) {
        Collection<String> ids = clients.stream()
                .map(Client::getId)
                .filter(Objects::nonNull)
                .toList();

        if (ids.isEmpty()) {
            return Map.of();
        }

        Map<String, int[]> stats = new HashMap<>();
        LocalDate today = LocalDate.now();
        for (FollowUp followUp : followUpRepository.findByClientIdIn(ids)) {
            if (followUp.getClientId() == null) {
                continue;
            }
            int[] counts = stats.computeIfAbsent(
                    followUp.getClientId(), key -> new int[2]);
            counts[0]++;
            if (isOverdue(followUp, today)) {
                counts[1]++;
            }
        }
        return stats;
    }

    private boolean matches(Client client, String needle) {
        return contains(client.getName(), needle)
                || contains(client.getCompanyName(), needle)
                || contains(client.getEmail(), needle)
                || contains(client.getIndustry(), needle)
                || contains(client.getNotes(), needle);
    }

    private boolean contains(String value, String needle) {
        return value != null
                && value.toLowerCase(Locale.ROOT).contains(needle);
    }

    private static final int[] EMPTY_COUNTS = new int[] {0, 0};

    static String normalizeEmail(String value) {
        return value == null ? null
                : value.trim().toLowerCase(Locale.ROOT);
    }

    static boolean isOverdue(FollowUp followUp, LocalDate today) {
        return followUp.getDueDate() != null
                && followUp.getDueDate().isBefore(today)
                && !followUp.isCompleted();
    }

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

    private static boolean equalsIgnoreCase(String left, String right) {
        return left != null && right != null && left.equalsIgnoreCase(right);
    }
}
