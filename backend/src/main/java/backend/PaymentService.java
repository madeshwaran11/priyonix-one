package backend;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Service;

/**
 * Business rules for invoice payments.
 *
 * Responsibilities:
 *  - require the target invoice to exist and to have been issued (not DRAFT);
 *  - reject overpayments (a payment can never push an invoice past its total);
 *  - reject duplicate references so the same transaction cannot be recorded
 *    twice;
 *  - keep balances/statuses automatic by deriving everything from the payment
 *    records (the invoice stores no balance of its own).
 */
@Service
public class PaymentService {
    @org.springframework.beans.factory.annotation.Autowired
    private LegacyIntegrityService integrity;

    public static final Set<String> ALLOWED_METHODS =
            Set.of("CASH", "BANK_TRANSFER", "CARD", "UPI", "CHEQUE", "OTHER");

    public static final String DEFAULT_METHOD = "OTHER";

    private static final BigDecimal ZERO =
            BigDecimal.ZERO.setScale(2, java.math.RoundingMode.HALF_UP);

    private final PaymentRepository paymentRepository;
    private final InvoiceRepository invoiceRepository;
    private final ClientRepository clientRepository;
    private final InvoiceService invoiceService;

    public PaymentService(
            PaymentRepository paymentRepository,
            InvoiceRepository invoiceRepository,
            ClientRepository clientRepository,
            InvoiceService invoiceService) {
        this.paymentRepository = paymentRepository;
        this.invoiceRepository = invoiceRepository;
        this.clientRepository = clientRepository;
        this.invoiceService = invoiceService;
    }

    // ----- queries ------------------------------------------------------

    public PagedResult<PaymentResponse> list(
            String search,
            String invoiceId,
            String method,
            LocalDate from,
            LocalDate to,
            int page,
            int size) {

        String methodFilter = method == null || method.isBlank()
                ? null : requireEnum(method, ALLOWED_METHODS, "Payment method");
        String invoiceFilter = blankToNull(invoiceId);
        String needle = search == null ? null
                : search.trim().toLowerCase(Locale.ROOT);

        List<Payment> filtered = paymentRepository
                .findAllByOrderByCreatedAtDesc()
                .stream()
                .filter(payment -> methodFilter == null
                        || methodFilter.equalsIgnoreCase(payment.getMethod()))
                .filter(payment -> invoiceFilter == null
                        || invoiceFilter.equals(payment.getInvoiceId()))
                .filter(payment -> from == null
                        || (payment.getPaymentDate() != null
                                && !payment.getPaymentDate().isBefore(from)))
                .filter(payment -> to == null
                        || (payment.getPaymentDate() != null
                                && !payment.getPaymentDate().isAfter(to)))
                .filter(payment -> needle == null || needle.isEmpty()
                        || matches(payment, needle))
                .toList();

        return PagedResult.of(buildResponses(filtered), page, size);
    }

    /** All payments applied to a given invoice, newest first. */
    public List<PaymentResponse> listByInvoice(String invoiceId) {
        // Validate the invoice reference (404 when missing).
        invoiceService.getEntity(invoiceId);
        return buildResponses(paymentRepository.findByInvoiceId(invoiceId));
    }

    public Payment getEntity(String id) {
        return paymentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Payment not found: " + id));
    }

    public PaymentResponse getById(String id) {
        return buildResponses(List.of(getEntity(id))).get(0);
    }

    // ----- mutations ----------------------------------------------------

    @org.springframework.transaction.annotation.Transactional("workspaceTransactionManager")
    public PaymentResponse create(PaymentRequest request, String recordedBy) {
        if (integrity != null) integrity.touchInvoice(request.invoiceId());
        Invoice invoice = invoiceService.getEntity(request.invoiceId());
        requireIssued(invoice);

        BigDecimal amount = InvoiceService.round(request.amount());
        String reference = blankToNull(request.referenceNumber());

        BigDecimal alreadyPaid = invoiceService.amountPaid(invoice.getId());
        ensureNoOverpayment(invoice, alreadyPaid, amount);

        if (reference != null
                && paymentRepository.existsByReferenceNumberIgnoreCase(
                        reference)) {
            throw new DuplicateResourceException(
                    "A payment with reference " + reference
                            + " already exists");
        }

        Payment payment = new Payment();
        payment.setRecordedBy(recordedBy);
        payment.setCreatedAt(Instant.now());
        apply(payment, request, invoice.getId(), amount, reference);
        payment.setUpdatedAt(Instant.now());

        Payment saved = paymentRepository.save(payment);
        return buildResponses(List.of(saved)).get(0);
    }

    @org.springframework.transaction.annotation.Transactional("workspaceTransactionManager")
    public PaymentResponse update(String id, PaymentRequest request) {
        Payment existing = getEntity(id);
        if (integrity != null) {
            integrity.touchInvoice(existing.getInvoiceId());
            if (!existing.getInvoiceId().equals(request.invoiceId())) integrity.touchInvoice(request.invoiceId());
        }
        Invoice invoice = invoiceService.getEntity(request.invoiceId());
        requireIssued(invoice);

        BigDecimal amount = InvoiceService.round(request.amount());
        String reference = blankToNull(request.referenceNumber());

        // Paid amount on the target invoice, excluding this payment when the
        // payment stays on the same invoice.
        BigDecimal paid = invoiceService.amountPaid(invoice.getId());
        if (invoice.getId().equals(existing.getInvoiceId())) {
            BigDecimal current = existing.getAmount() == null
                    ? ZERO : existing.getAmount();
            paid = paid.subtract(current);
        }
        ensureNoOverpayment(invoice, paid, amount);

        if (reference != null) {
            Optional<Payment> clash =
                    paymentRepository.findByReferenceNumberIgnoreCase(
                            reference);
            if (clash.isPresent()
                    && !clash.get().getId().equals(existing.getId())) {
                throw new DuplicateResourceException(
                        "A payment with reference " + reference
                                + " already exists");
            }
        }

        apply(existing, request, invoice.getId(), amount, reference);
        // recordedBy and createdAt are preserved.
        existing.setUpdatedAt(Instant.now());

        Payment saved = paymentRepository.save(existing);
        return buildResponses(List.of(saved)).get(0);
    }

    /** Callers must be administrators (see SecurityConfig). */
    @org.springframework.transaction.annotation.Transactional("workspaceTransactionManager")
    public void delete(String id) {
        Payment payment = getEntity(id);
        if (integrity != null) integrity.touchInvoice(payment.getInvoiceId());
        paymentRepository.delete(payment);
    }

    // ----- helpers ------------------------------------------------------

    private void requireIssued(Invoice invoice) {
        if ("DRAFT".equalsIgnoreCase(invoice.getStatus())) {
            throw new InvalidRequestException(
                    "Cannot record a payment against a draft invoice. "
                            + "Issue the invoice first");
        }
    }

    private void ensureNoOverpayment(
            Invoice invoice, BigDecimal alreadyPaid, BigDecimal amount) {

        BigDecimal total = InvoiceService.totals(invoice).total();
        if (amount.signum() <= 0) throw new InvalidRequestException("Payment amount must be greater than zero.");
        BigDecimal outstanding = InvoiceService.round(total.subtract(alreadyPaid));
        if (outstanding.signum() < 0) {
            outstanding = ZERO;
        }

        if (amount.compareTo(outstanding) > 0) {
            throw new InvalidRequestException(
                    "Payment of " + amount + " exceeds the outstanding balance "
                            + "of " + outstanding + " on invoice "
                            + invoice.getInvoiceNumber());
        }
    }

    private void apply(
            Payment payment,
            PaymentRequest request,
            String invoiceId,
            BigDecimal amount,
            String reference) {

        payment.setInvoiceId(invoiceId);
        payment.setAmount(amount);
        payment.setPaymentDate(request.paymentDate());
        payment.setMethod(normalizeEnum(
                request.method(), ALLOWED_METHODS, DEFAULT_METHOD,
                "Payment method"));
        payment.setReferenceNumber(reference);
        payment.setNotes(blankToNull(request.notes()));
    }

    private List<PaymentResponse> buildResponses(List<Payment> payments) {
        Map<String, Invoice> invoices = resolveInvoices(payments);
        Map<String, Client> clients = resolveClients(invoices.values());

        List<PaymentResponse> responses = new ArrayList<>(payments.size());
        for (Payment payment : payments) {
            Invoice invoice = lookup(invoices, payment.getInvoiceId());
            Client client = invoice == null
                    ? null : lookup(clients, invoice.getClientId());
            responses.add(new PaymentResponse(
                    payment.getId(),
                    payment.getInvoiceId(),
                    invoice == null ? null : invoice.getInvoiceNumber(),
                    invoice == null ? null : invoice.getClientId(),
                    client == null ? null : client.getName(),
                    payment.getAmount(),
                    payment.getPaymentDate(),
                    payment.getMethod(),
                    payment.getReferenceNumber(),
                    payment.getNotes(),
                    payment.getRecordedBy(),
                    payment.getCreatedAt(),
                    payment.getUpdatedAt()));
        }
        return responses;
    }

    private Map<String, Invoice> resolveInvoices(List<Payment> payments) {
        Set<String> ids = new LinkedHashSet<>();
        for (Payment payment : payments) {
            if (payment.getInvoiceId() != null) {
                ids.add(payment.getInvoiceId());
            }
        }
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<String, Invoice> map = new HashMap<>();
        for (Invoice invoice : invoiceRepository.findAllById(ids)) {
            map.put(invoice.getId(), invoice);
        }
        return map;
    }

    private Map<String, Client> resolveClients(java.util.Collection<Invoice> invoices) {
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

    private boolean matches(Payment payment, String needle) {
        return contains(payment.getReferenceNumber(), needle)
                || contains(payment.getNotes(), needle)
                || contains(payment.getRecordedBy(), needle);
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
