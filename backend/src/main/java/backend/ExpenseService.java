package backend;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.springframework.stereotype.Service;

/**
 * Business rules for business expenses.
 *
 * Responsibilities:
 *  - validate the category, payment method and positive amount;
 *  - create every expense in the PENDING state;
 *  - expose an explicit, role-checked status transition (approve / reject /
 *    reimburse) that records who decided and when;
 *  - keep editing limited to the expense details so the approval trail cannot
 *    be rewritten through the generic update.
 */
@Service
public class ExpenseService {

    public static final Set<String> ALLOWED_STATUSES =
            Set.of("PENDING", "APPROVED", "REJECTED", "REIMBURSED");

    public static final Set<String> ALLOWED_CATEGORIES = Set.of(
            "TRAVEL", "EQUIPMENT", "SOFTWARE", "OFFICE",
            "MARKETING", "UTILITIES", "SALARY", "CONSULTING", "OTHER");

    public static final Set<String> ALLOWED_PAYMENT_METHODS =
            PaymentService.ALLOWED_METHODS;

    public static final String DEFAULT_STATUS = "PENDING";
    public static final String DEFAULT_CATEGORY = "OTHER";
    public static final String DEFAULT_PAYMENT_METHOD = "OTHER";

    private final ExpenseRepository expenseRepository;

    public ExpenseService(ExpenseRepository expenseRepository) {
        this.expenseRepository = expenseRepository;
    }

    // ----- queries ------------------------------------------------------

    public PagedResult<ExpenseResponse> list(
            String search,
            String status,
            String category,
            LocalDate from,
            LocalDate to,
            int page,
            int size) {

        String statusFilter = status == null || status.isBlank()
                ? null : requireEnum(status, ALLOWED_STATUSES, "Status");
        String categoryFilter = category == null || category.isBlank()
                ? null : requireEnum(category, ALLOWED_CATEGORIES, "Category");
        String needle = search == null ? null
                : search.trim().toLowerCase(Locale.ROOT);

        List<Expense> filtered = expenseRepository
                .findAllByOrderByCreatedAtDesc()
                .stream()
                .filter(expense -> statusFilter == null
                        || statusFilter.equalsIgnoreCase(expense.getStatus()))
                .filter(expense -> categoryFilter == null
                        || categoryFilter.equalsIgnoreCase(
                                expense.getCategory()))
                .filter(expense -> from == null
                        || (expense.getExpenseDate() != null
                                && !expense.getExpenseDate().isBefore(from)))
                .filter(expense -> to == null
                        || (expense.getExpenseDate() != null
                                && !expense.getExpenseDate().isAfter(to)))
                .filter(expense -> needle == null || needle.isEmpty()
                        || matches(expense, needle))
                .toList();

        return PagedResult.of(toResponses(filtered), page, size);
    }

    public Expense getEntity(String id) {
        return expenseRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Expense not found: " + id));
    }

    public ExpenseResponse getById(String id) {
        return toResponses(List.of(getEntity(id))).get(0);
    }

    // ----- mutations ----------------------------------------------------

    public ExpenseResponse create(ExpenseRequest request, String createdBy) {
        Expense expense = new Expense();
        expense.setStatus(DEFAULT_STATUS);
        expense.setCreatedBy(createdBy);
        expense.setCreatedAt(Instant.now());
        apply(expense, request);
        expense.setUpdatedAt(Instant.now());

        Expense saved = expenseRepository.save(expense);
        return toResponses(List.of(saved)).get(0);
    }

    /** Updates the expense details only; the status is preserved. */
    public ExpenseResponse update(String id, ExpenseRequest request) {
        Expense existing = getEntity(id);
        if (!Set.of("PENDING", "REJECTED").contains(existing.getStatus())) {
            throw new ConflictException("Approved or reimbursed expenses cannot be changed.");
        }
        apply(existing, request);
        // status, approvedBy, approvedAt, createdBy and createdAt preserved.
        existing.setUpdatedAt(Instant.now());

        Expense saved = expenseRepository.save(existing);
        return toResponses(List.of(saved)).get(0);
    }

    /**
     * Administrative approval decision. Callers must be administrators (see
     * SecurityConfig); moving back to PENDING clears the approval trail.
     */
    public ExpenseResponse updateStatus(String id, String status, String actor) {
        Expense expense = getEntity(id);
        String normalized = requireEnum(status, ALLOWED_STATUSES, "Status");
        String current = expense.getStatus();
        boolean allowed = normalized.equals(current)
                || ("PENDING".equals(current) && Set.of("APPROVED", "REJECTED").contains(normalized))
                || ("APPROVED".equals(current) && "REIMBURSED".equals(normalized))
                || ("REJECTED".equals(current) && "PENDING".equals(normalized));
        if (!allowed) throw new InvalidRequestException("This expense status change is not allowed.");

        expense.setStatus(normalized);
        if (DEFAULT_STATUS.equals(normalized)) {
            expense.setApprovedBy(null);
            expense.setApprovedAt(null);
        } else {
            expense.setApprovedBy(actor);
            expense.setApprovedAt(Instant.now());
        }
        expense.setUpdatedAt(Instant.now());

        Expense saved = expenseRepository.save(expense);
        return toResponses(List.of(saved)).get(0);
    }

    /** Callers must be administrators (see SecurityConfig). */
    public void delete(String id) {
        Expense expense = getEntity(id);
        if (Set.of("APPROVED", "REIMBURSED").contains(expense.getStatus())) {
            throw new ConflictException("Approved or reimbursed expenses cannot be deleted.");
        }
        expenseRepository.delete(expense);
    }

    // ----- helpers ------------------------------------------------------

    private void apply(Expense expense, ExpenseRequest request) {
        expense.setTitle(request.title());
        expense.setCategory(normalizeEnum(
                request.category(), ALLOWED_CATEGORIES, DEFAULT_CATEGORY,
                "Category"));
        expense.setAmount(InvoiceService.round(request.amount()));
        expense.setExpenseDate(request.expenseDate());
        expense.setVendor(blankToNull(request.vendor()));
        expense.setPaymentMethod(normalizeEnum(
                request.paymentMethod(), ALLOWED_PAYMENT_METHODS,
                DEFAULT_PAYMENT_METHOD, "Payment method"));
        expense.setReceiptReference(blankToNull(request.receiptReference()));
        expense.setNotes(blankToNull(request.notes()));
    }

    private List<ExpenseResponse> toResponses(List<Expense> expenses) {
        List<ExpenseResponse> responses = new ArrayList<>(expenses.size());
        for (Expense expense : expenses) {
            responses.add(new ExpenseResponse(
                    expense.getId(),
                    expense.getTitle(),
                    expense.getCategory(),
                    expense.getAmount(),
                    expense.getExpenseDate(),
                    expense.getVendor(),
                    expense.getPaymentMethod(),
                    expense.getReceiptReference(),
                    expense.getNotes(),
                    expense.getStatus(),
                    expense.getApprovedBy(),
                    expense.getApprovedAt(),
                    expense.getCreatedBy(),
                    expense.getCreatedAt(),
                    expense.getUpdatedAt()));
        }
        return responses;
    }

    private boolean matches(Expense expense, String needle) {
        return contains(expense.getTitle(), needle)
                || contains(expense.getVendor(), needle)
                || contains(expense.getReceiptReference(), needle)
                || contains(expense.getNotes(), needle);
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
