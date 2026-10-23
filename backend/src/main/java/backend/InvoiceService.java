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
import java.util.Objects;
import java.util.Set;

import org.springframework.stereotype.Service;

/**
 * Business rules for invoices.
 *
 * Responsibilities:
 *  - generate unique invoice numbers;
 *  - validate the client/project references, the date ordering, the line items
 *    and the discount/tax ranges;
 *  - compute every money figure (subtotal, tax, total, paid, balance) with
 *    BigDecimal HALF_UP rounding, so stored data is never trusted for amounts;
 *  - derive the effective status (PAID / PARTIALLY_PAID / OVERDUE / SENT) from
 *    the recorded payments and the due date;
 *  - protect invoices that still have payments from accidental deletion.
 *
 * The stored {@code status} is only the issue state (DRAFT or SENT); the rest
 * is always computed so it can never fall out of sync with the payments.
 */
@Service
public class InvoiceService {
    @org.springframework.beans.factory.annotation.Autowired
    private LegacyIntegrityService integrity;

    /** Issue states a client may set directly. */
    public static final Set<String> ISSUE_STATUSES = Set.of("DRAFT", "SENT");

    /** Every status the API can report (issue states plus derived states). */
    public static final Set<String> EFFECTIVE_STATUSES =
            Set.of("DRAFT", "SENT", "PARTIALLY_PAID", "PAID", "OVERDUE");

    public static final String DEFAULT_STATUS = "DRAFT";

    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

    private final InvoiceRepository invoiceRepository;
    private final PaymentRepository paymentRepository;
    private final ClientRepository clientRepository;
    private final ProjectRepository projectRepository;

    public InvoiceService(
            InvoiceRepository invoiceRepository,
            PaymentRepository paymentRepository,
            ClientRepository clientRepository,
            ProjectRepository projectRepository) {
        this.invoiceRepository = invoiceRepository;
        this.paymentRepository = paymentRepository;
        this.clientRepository = clientRepository;
        this.projectRepository = projectRepository;
    }

    // ----- queries ------------------------------------------------------

    public PagedResult<InvoiceResponse> list(
            String search,
            String status,
            String clientId,
            String projectId,
            boolean overdueOnly,
            int page,
            int size) {

        List<Invoice> all = invoiceRepository.findAllByOrderByCreatedAtDesc();
        Map<String, BigDecimal> paid = paidByInvoice(all);
        Map<String, Integer> counts = paymentCounts(all);
        List<Invoice> filtered = filter(
                all, paid, search, status, clientId, projectId, overdueOnly);

        return PagedResult.of(
                buildResponses(filtered, paid, counts), page, size);
    }

    /**
     * Every matching invoice (no pagination), for the CSV export. Uses the
     * same filtering as {@link #list} so exports and screens never disagree.
     */
    public List<InvoiceResponse> listAll(
            String search,
            String status,
            String clientId,
            String projectId,
            boolean overdueOnly) {

        List<Invoice> all = invoiceRepository.findAllByOrderByCreatedAtDesc();
        Map<String, BigDecimal> paid = paidByInvoice(all);
        List<Invoice> filtered = filter(
                all, paid, search, status, clientId, projectId, overdueOnly);

        return buildResponses(filtered, paid, paymentCounts(all));
    }

    private List<Invoice> filter(
            List<Invoice> all,
            Map<String, BigDecimal> paid,
            String search,
            String status,
            String clientId,
            String projectId,
            boolean overdueOnly) {

        String statusFilter = status == null || status.isBlank()
                ? null : requireEnum(status, EFFECTIVE_STATUSES, "Status");
        String clientFilter = blankToNull(clientId);
        String projectFilter = blankToNull(projectId);
        String needle = search == null ? null
                : search.trim().toLowerCase(Locale.ROOT);
        LocalDate today = LocalDate.now();

        List<Invoice> filtered = new ArrayList<>();
        for (Invoice invoice : all) {
            BigDecimal amountPaid = paid.getOrDefault(invoice.getId(), ZERO);
            if (statusFilter != null && !statusFilter.equalsIgnoreCase(
                    effectiveStatus(invoice, amountPaid, today))) {
                continue;
            }
            if (overdueOnly && !isOverdue(invoice, amountPaid, today)) {
                continue;
            }
            if (clientFilter != null
                    && !clientFilter.equals(invoice.getClientId())) {
                continue;
            }
            if (projectFilter != null
                    && !projectFilter.equals(invoice.getProjectId())) {
                continue;
            }
            if (needle != null && !needle.isEmpty()
                    && !matches(invoice, needle)) {
                continue;
            }
            filtered.add(invoice);
        }
        return filtered;
    }

    public Invoice getEntity(String id) {
        return invoiceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Invoice not found: " + id));
    }

    public InvoiceResponse getById(String id) {
        return buildResponses(List.of(getEntity(id))).get(0);
    }

    /** Sum of all payments recorded against an invoice, rounded to 2 dp. */
    public BigDecimal amountPaid(String invoiceId) {
        BigDecimal sum = BigDecimal.ZERO;
        for (Payment payment : paymentRepository.findByInvoiceId(invoiceId)) {
            if (payment.getAmount() != null) {
                sum = sum.add(payment.getAmount());
            }
        }
        return round(sum);
    }

    // ----- mutations ----------------------------------------------------

    @org.springframework.transaction.annotation.Transactional("workspaceTransactionManager")
    public InvoiceResponse create(InvoiceRequest request, String createdBy) {
        Validated validated = validate(request);

        Invoice invoice = new Invoice();
        invoice.setInvoiceNumber(generateInvoiceNumber());
        invoice.setCreatedBy(createdBy);
        invoice.setCreatedAt(Instant.now());
        apply(invoice, request, validated);
        invoice.setUpdatedAt(Instant.now());

        Invoice saved = invoiceRepository.save(invoice);
        return buildResponses(List.of(saved)).get(0);
    }

    @org.springframework.transaction.annotation.Transactional("workspaceTransactionManager")
    public InvoiceResponse update(String id, InvoiceRequest request) {
        if (integrity != null) integrity.touchInvoice(id);
        Invoice existing = getEntity(id);
        if (paymentRepository.countByInvoiceId(id) > 0) {
            throw new ConflictException("An invoice with recorded payments cannot be changed.");
        }
        Validated validated = validate(request);

        apply(existing, request, validated);
        // invoiceNumber, createdBy and createdAt are preserved.
        existing.setUpdatedAt(Instant.now());

        Invoice saved = invoiceRepository.save(existing);
        return buildResponses(List.of(saved)).get(0);
    }

    /**
     * Deletes an invoice. An invoice that still has payments is protected
     * unless {@code cascade} is true, in which case those payments are removed
     * first. Callers must be administrators (see SecurityConfig).
     */
    @org.springframework.transaction.annotation.Transactional("workspaceTransactionManager")
    public void delete(String id, boolean cascade) {
        if (integrity != null) integrity.touchInvoice(id);
        Invoice invoice = getEntity(id);
        long payments = paymentRepository.countByInvoiceId(id);

        if (payments > 0) {
            throw new ConflictException(
                    "Invoices with recorded payments cannot be deleted. Preserve the payment history.");
        }

        if (cascade && payments > 0) {
            paymentRepository.deleteByInvoiceId(id);
        }

        invoiceRepository.delete(invoice);
    }

    // ----- validation / mapping ----------------------------------------

    private Validated validate(InvoiceRequest request) {
        if (clientRepository.findById(request.clientId()).isEmpty()) {
            throw new InvalidRequestException(
                    "Invoice references an unknown client: "
                            + request.clientId());
        }

        String projectId = blankToNull(request.projectId());
        if (projectId != null
                && projectRepository.findById(projectId).isEmpty()) {
            throw new InvalidRequestException(
                    "Invoice references an unknown project: " + projectId);
        }

        if (request.dueDate().isBefore(request.issueDate())) {
            throw new InvalidRequestException(
                    "Due date cannot be before the issue date");
        }

        List<InvoiceLineItem> lineItems = mapLineItems(request.lineItems());
        if (lineItems.isEmpty()) {
            throw new InvalidRequestException(
                    "At least one line item is required");
        }
        for (InvoiceLineItem item : lineItems) {
            if (item.getQuantity() == null || item.getQuantity().signum() <= 0) {
                throw new InvalidRequestException(
                        "Line quantity must be greater than 0");
            }
            if (item.getUnitPrice() == null
                    || item.getUnitPrice().signum() < 0) {
                throw new InvalidRequestException(
                        "Line unit price cannot be negative");
            }
        }

        BigDecimal taxRate = request.taxRate() == null
                ? BigDecimal.ZERO : request.taxRate();
        if (taxRate.signum() < 0 || taxRate.compareTo(HUNDRED) > 0) {
            throw new InvalidRequestException(
                    "Tax rate must be between 0 and 100");
        }

        BigDecimal subtotal = subtotalOf(lineItems);
        BigDecimal discount = request.discount() == null
                ? BigDecimal.ZERO : request.discount();
        if (discount.signum() < 0) {
            throw new InvalidRequestException("Discount cannot be negative");
        }
        if (discount.compareTo(subtotal) > 0) {
            throw new InvalidRequestException(
                    "Discount cannot exceed the subtotal");
        }

        String status = normalizeEnum(
                request.status(), ISSUE_STATUSES, DEFAULT_STATUS, "Status");

        return new Validated(status, taxRate, discount, lineItems);
    }

    private void apply(
            Invoice invoice, InvoiceRequest request, Validated validated) {

        invoice.setClientId(request.clientId());
        invoice.setProjectId(blankToNull(request.projectId()));
        invoice.setIssueDate(request.issueDate());
        invoice.setDueDate(request.dueDate());
        invoice.setStatus(validated.status());
        invoice.setLineItems(validated.lineItems());
        invoice.setTaxRate(validated.taxRate());
        invoice.setDiscount(validated.discount());
        invoice.setNotes(blankToNull(request.notes()));
    }

    private List<InvoiceLineItem> mapLineItems(
            List<InvoiceLineItemRequest> requests) {
        List<InvoiceLineItem> items = new ArrayList<>();
        if (requests == null) {
            return items;
        }
        for (InvoiceLineItemRequest request : requests) {
            InvoiceLineItem item = new InvoiceLineItem();
            item.setDescription(request.description());
            item.setQuantity(request.quantity());
            item.setUnitPrice(request.unitPrice());
            items.add(item);
        }
        return items;
    }

    private record Validated(
            String status,
            BigDecimal taxRate,
            BigDecimal discount,
            List<InvoiceLineItem> lineItems) {
    }

    // ----- responses ----------------------------------------------------

    private List<InvoiceResponse> buildResponses(List<Invoice> invoices) {
        return buildResponses(
                invoices,
                paidByInvoice(invoices),
                paymentCounts(invoices));
    }

    private List<InvoiceResponse> buildResponses(
            List<Invoice> invoices,
            Map<String, BigDecimal> paid,
            Map<String, Integer> counts) {

        Map<String, Client> clients = resolveClients(invoices);
        Map<String, Project> projects = resolveProjects(invoices);
        LocalDate today = LocalDate.now();

        List<InvoiceResponse> responses = new ArrayList<>(invoices.size());
        for (Invoice invoice : invoices) {
            responses.add(toResponse(
                    invoice, clients, projects, paid, counts, today));
        }
        return responses;
    }

    private InvoiceResponse toResponse(
            Invoice invoice,
            Map<String, Client> clients,
            Map<String, Project> projects,
            Map<String, BigDecimal> paid,
            Map<String, Integer> counts,
            LocalDate today) {

        InvoiceTotals totals = totals(invoice);
        BigDecimal amountPaid = paid.getOrDefault(invoice.getId(), ZERO);
        BigDecimal balance = round(totals.total().subtract(amountPaid));
        if (balance.signum() < 0) {
            balance = ZERO;
        }

        Client client = lookup(clients, invoice.getClientId());
        Project project = lookup(projects, invoice.getProjectId());

        List<InvoiceLineItemResponse> lines = new ArrayList<>();
        for (InvoiceLineItem item : invoice.getLineItems()) {
            lines.add(new InvoiceLineItemResponse(
                    item.getDescription(),
                    item.getQuantity(),
                    item.getUnitPrice(),
                    lineAmount(item)));
        }

        return new InvoiceResponse(
                invoice.getId(),
                invoice.getInvoiceNumber(),
                invoice.getClientId(),
                client == null ? null : client.getName(),
                client == null ? null : client.getCompanyName(),
                invoice.getProjectId(),
                project == null ? null : project.getName(),
                project == null ? null : project.getProjectCode(),
                invoice.getIssueDate(),
                invoice.getDueDate(),
                effectiveStatus(invoice, amountPaid, today),
                isOverdue(invoice, amountPaid, today),
                lines,
                totals.subtotal(),
                totals.discount(),
                totals.taxableAmount(),
                totals.taxRate(),
                totals.taxAmount(),
                totals.total(),
                amountPaid,
                balance,
                counts.getOrDefault(invoice.getId(), 0),
                invoice.getNotes(),
                invoice.getCreatedBy(),
                invoice.getCreatedAt(),
                invoice.getUpdatedAt());
    }

    private Map<String, Client> resolveClients(List<Invoice> invoices) {
        Set<String> ids = new LinkedHashSet<>();
        for (Invoice invoice : invoices) {
            if (invoice.getClientId() != null) {
                ids.add(invoice.getClientId());
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

    private Map<String, Project> resolveProjects(List<Invoice> invoices) {
        Set<String> ids = new LinkedHashSet<>();
        for (Invoice invoice : invoices) {
            if (invoice.getProjectId() != null) {
                ids.add(invoice.getProjectId());
            }
        }
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<String, Project> map = new HashMap<>();
        for (Project project : projectRepository.findAllById(ids)) {
            map.put(project.getId(), project);
        }
        return map;
    }

    private Map<String, BigDecimal> paidByInvoice(List<Invoice> invoices) {
        Collection<String> ids = invoiceIds(invoices);
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<String, BigDecimal> map = new HashMap<>();
        for (Payment payment : paymentRepository.findByInvoiceIdIn(ids)) {
            if (payment.getInvoiceId() == null
                    || payment.getAmount() == null) {
                continue;
            }
            map.merge(payment.getInvoiceId(), payment.getAmount(),
                    BigDecimal::add);
        }
        map.replaceAll((key, value) -> round(value));
        return map;
    }

    private Map<String, Integer> paymentCounts(List<Invoice> invoices) {
        Collection<String> ids = invoiceIds(invoices);
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<String, Integer> map = new HashMap<>();
        for (Payment payment : paymentRepository.findByInvoiceIdIn(ids)) {
            if (payment.getInvoiceId() != null) {
                map.merge(payment.getInvoiceId(), 1, Integer::sum);
            }
        }
        return map;
    }

    private Collection<String> invoiceIds(List<Invoice> invoices) {
        return invoices.stream()
                .map(Invoice::getId)
                .filter(Objects::nonNull)
                .toList();
    }

    // ----- money math ---------------------------------------------------

    /** Rounds a money value to 2 decimals using HALF_UP. */
    public static BigDecimal round(BigDecimal value) {
        return value == null
                ? null
                : value.setScale(2, RoundingMode.HALF_UP);
    }

    static BigDecimal lineAmount(InvoiceLineItem item) {
        if (item == null || item.getQuantity() == null
                || item.getUnitPrice() == null) {
            return ZERO;
        }
        return round(item.getQuantity().multiply(item.getUnitPrice()));
    }

    static BigDecimal subtotalOf(List<InvoiceLineItem> lineItems) {
        BigDecimal sum = BigDecimal.ZERO;
        if (lineItems != null) {
            for (InvoiceLineItem item : lineItems) {
                sum = sum.add(lineAmount(item));
            }
        }
        return round(sum);
    }

    /** Live totals for a persisted invoice. */
    public static InvoiceTotals totals(Invoice invoice) {
        if (invoice == null) {
            return new InvoiceTotals(ZERO, ZERO, ZERO, BigDecimal.ZERO, ZERO, ZERO);
        }
        return totals(
                invoice.getLineItems(),
                invoice.getTaxRate(),
                invoice.getDiscount());
    }

    static InvoiceTotals totals(
            List<InvoiceLineItem> lineItems,
            BigDecimal taxRate,
            BigDecimal discount) {

        BigDecimal subtotal = subtotalOf(lineItems);

        BigDecimal disc = discount == null ? ZERO : round(discount);
        if (disc.signum() < 0) {
            disc = ZERO;
        }
        if (disc.compareTo(subtotal) > 0) {
            disc = subtotal;
        }

        BigDecimal taxable = round(subtotal.subtract(disc));
        BigDecimal rate = taxRate == null ? BigDecimal.ZERO : taxRate;
        BigDecimal tax = round(taxable
                .multiply(rate)
                .divide(HUNDRED, 6, RoundingMode.HALF_UP));
        BigDecimal total = round(taxable.add(tax));

        return new InvoiceTotals(subtotal, disc, taxable, rate, tax, total);
    }

    /** The computed money figures for an invoice. */
    public record InvoiceTotals(
            BigDecimal subtotal,
            BigDecimal discount,
            BigDecimal taxableAmount,
            BigDecimal taxRate,
            BigDecimal taxAmount,
            BigDecimal total) {
    }

    // ----- status derivation -------------------------------------------

    static String effectiveStatus(
            Invoice invoice, BigDecimal amountPaid, LocalDate today) {

        if ("DRAFT".equalsIgnoreCase(invoice.getStatus())) {
            return "DRAFT";
        }

        BigDecimal paid = amountPaid == null ? ZERO : amountPaid;
        BigDecimal total = totals(invoice).total();

        if (total.signum() <= 0) {
            return "SENT";
        }
        if (paid.compareTo(total) >= 0) {
            return "PAID";
        }
        if (paid.signum() > 0) {
            return "PARTIALLY_PAID";
        }
        if (invoice.getDueDate() != null
                && invoice.getDueDate().isBefore(today)) {
            return "OVERDUE";
        }
        return "SENT";
    }

    static boolean isOverdue(
            Invoice invoice, BigDecimal amountPaid, LocalDate today) {

        if (invoice == null
                || "DRAFT".equalsIgnoreCase(invoice.getStatus())
                || invoice.getDueDate() == null
                || !invoice.getDueDate().isBefore(today)) {
            return false;
        }
        BigDecimal balance = totals(invoice).total()
                .subtract(amountPaid == null ? ZERO : amountPaid);
        return balance.signum() > 0;
    }

    // ----- helpers ------------------------------------------------------

    private String generateInvoiceNumber() {
        if (integrity != null) return integrity.nextInvoiceNumber();
        String prefix = "INV-" + LocalDate.now().getYear() + "-";
        long sequence =
                invoiceRepository.countByInvoiceNumberStartingWith(prefix) + 1;
        String candidate = prefix + String.format("%04d", sequence);
        while (invoiceRepository.existsByInvoiceNumberIgnoreCase(candidate)) {
            sequence++;
            candidate = prefix + String.format("%04d", sequence);
        }
        return candidate;
    }

    private boolean matches(Invoice invoice, String needle) {
        return contains(invoice.getInvoiceNumber(), needle)
                || contains(invoice.getNotes(), needle)
                || contains(invoice.getCreatedBy(), needle);
    }

    private boolean contains(String value, String needle) {
        return value != null
                && value.toLowerCase(Locale.ROOT).contains(needle);
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
}
