package backend;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

import org.springframework.stereotype.Service;

/**
 * Financial reporting.
 *
 * Every figure is summed live from the invoice, payment and expense
 * collections; nothing is hardcoded. The three series are kept independent to
 * avoid double-counting:
 * <ul>
 *   <li>revenue recognised — issued invoice totals by issue date;</li>
 *   <li>cash collected — payments by payment date;</li>
 *   <li>approved spend — APPROVED/REIMBURSED expenses by expense date.</li>
 * </ul>
 * Profit is defined explicitly as collected cash minus approved spend.
 */
@Service
public class FinanceService {

    public static final String PROFIT_DEFINITION =
            "Net cash profit = collected payments minus approved expenses "
                    + "(cash basis).";

    private static final BigDecimal ZERO =
            BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

    private final InvoiceRepository invoiceRepository;
    private final PaymentRepository paymentRepository;
    private final ExpenseRepository expenseRepository;

    public FinanceService(
            InvoiceRepository invoiceRepository,
            PaymentRepository paymentRepository,
            ExpenseRepository expenseRepository) {
        this.invoiceRepository = invoiceRepository;
        this.paymentRepository = paymentRepository;
        this.expenseRepository = expenseRepository;
    }

    public FinancialSummaryResponse summary(LocalDate from, LocalDate to) {
        LocalDate today = LocalDate.now();

        List<Invoice> invoices = filterByDate(
                invoiceRepository.findAllByOrderByCreatedAtDesc(),
                Invoice::getIssueDate, from, to);
        List<Payment> payments = filterByDate(
                paymentRepository.findAllByOrderByCreatedAtDesc(),
                Payment::getPaymentDate, from, to);
        List<Expense> expenses = filterByDate(
                expenseRepository.findAllByOrderByCreatedAtDesc(),
                Expense::getExpenseDate, from, to);

        Map<String, BigDecimal> paidByInvoice =
                allTimePaidByInvoice(invoices);

        BigDecimal totalInvoiced = ZERO;
        BigDecimal totalOutstanding = ZERO;
        BigDecimal overdueAmount = ZERO;
        BigDecimal draftTotal = ZERO;
        int invoiceCount = 0;
        int draftCount = 0;
        int paidCount = 0;
        int overdueCount = 0;

        for (Invoice invoice : invoices) {
            InvoiceService.InvoiceTotals totals =
                    InvoiceService.totals(invoice);

            if ("DRAFT".equalsIgnoreCase(invoice.getStatus())) {
                draftCount++;
                draftTotal = draftTotal.add(totals.total());
                continue;
            }

            invoiceCount++;
            totalInvoiced = totalInvoiced.add(totals.total());

            BigDecimal paid = paidByInvoice.getOrDefault(
                    invoice.getId(), ZERO);
            BigDecimal balance = totals.total().subtract(paid);
            if (balance.signum() < 0) {
                balance = ZERO;
            }
            totalOutstanding = totalOutstanding.add(balance);

            if (totals.total().signum() > 0
                    && paid.compareTo(totals.total()) >= 0) {
                paidCount++;
            }
            if (InvoiceService.isOverdue(invoice, paid, today)) {
                overdueCount++;
                overdueAmount = overdueAmount.add(balance);
            }
        }

        BigDecimal totalCollected = ZERO;
        for (Payment payment : payments) {
            totalCollected = totalCollected.add(amount(payment.getAmount()));
        }

        BigDecimal approvedExpenses = ZERO;
        BigDecimal pendingExpenses = ZERO;
        int expenseCount = 0;
        int pendingCount = 0;

        for (Expense expense : expenses) {
            expenseCount++;
            BigDecimal value = amount(expense.getAmount());
            String status = expense.getStatus() == null
                    ? "" : expense.getStatus().toUpperCase(Locale.ROOT);
            if ("APPROVED".equals(status) || "REIMBURSED".equals(status)) {
                approvedExpenses = approvedExpenses.add(value);
            } else if ("PENDING".equals(status)) {
                pendingExpenses = pendingExpenses.add(value);
                pendingCount++;
            }
        }

        BigDecimal netProfit =
                InvoiceService.round(totalCollected.subtract(approvedExpenses));

        List<MonthlySummary> monthly =
                monthly(invoices, payments, expenses);

        return new FinancialSummaryResponse(
                from,
                to,
                InvoiceService.round(totalInvoiced),
                InvoiceService.round(totalCollected),
                InvoiceService.round(totalOutstanding),
                invoiceCount,
                draftCount,
                InvoiceService.round(draftTotal),
                paidCount,
                overdueCount,
                InvoiceService.round(overdueAmount),
                expenseCount,
                pendingCount,
                InvoiceService.round(approvedExpenses),
                InvoiceService.round(pendingExpenses),
                netProfit,
                PROFIT_DEFINITION,
                monthly);
    }

    /**
     * Groups the in-range records into calendar months. Draft invoices are
     * excluded from recognised revenue, matching the summary; only approved /
     * reimbursed expenses count as spend.
     */
    public List<MonthlySummary> monthly(
            List<Invoice> invoices,
            List<Payment> payments,
            List<Expense> expenses) {

        Set<YearMonth> months = new TreeSet<>();
        Map<YearMonth, BigDecimal> invoiced = new HashMap<>();
        Map<YearMonth, BigDecimal> collected = new HashMap<>();
        Map<YearMonth, BigDecimal> spend = new HashMap<>();
        Map<YearMonth, Integer> invoiceCounts = new HashMap<>();
        Map<YearMonth, Integer> paymentCounts = new HashMap<>();

        for (Invoice invoice : invoices) {
            if (invoice.getIssueDate() == null
                    || "DRAFT".equalsIgnoreCase(invoice.getStatus())) {
                continue;
            }
            YearMonth month = YearMonth.from(invoice.getIssueDate());
            months.add(month);
            invoiced.merge(month, InvoiceService.totals(invoice).total(),
                    BigDecimal::add);
            invoiceCounts.merge(month, 1, Integer::sum);
        }

        for (Payment payment : payments) {
            if (payment.getPaymentDate() == null) {
                continue;
            }
            YearMonth month = YearMonth.from(payment.getPaymentDate());
            months.add(month);
            collected.merge(month, amount(payment.getAmount()),
                    BigDecimal::add);
            paymentCounts.merge(month, 1, Integer::sum);
        }

        for (Expense expense : expenses) {
            if (expense.getExpenseDate() == null) {
                continue;
            }
            String status = expense.getStatus() == null
                    ? "" : expense.getStatus().toUpperCase(Locale.ROOT);
            if (!"APPROVED".equals(status) && !"REIMBURSED".equals(status)) {
                continue;
            }
            YearMonth month = YearMonth.from(expense.getExpenseDate());
            months.add(month);
            spend.merge(month, amount(expense.getAmount()), BigDecimal::add);
        }

        List<MonthlySummary> summaries = new ArrayList<>(months.size());
        for (YearMonth month : months) {
            BigDecimal monthCollected =
                    collected.getOrDefault(month, ZERO);
            BigDecimal monthSpend = spend.getOrDefault(month, ZERO);
            summaries.add(new MonthlySummary(
                    month.toString(),
                    InvoiceService.round(invoiced.getOrDefault(month, ZERO)),
                    InvoiceService.round(monthCollected),
                    InvoiceService.round(monthSpend),
                    InvoiceService.round(
                            monthCollected.subtract(monthSpend)),
                    invoiceCounts.getOrDefault(month, 0),
                    paymentCounts.getOrDefault(month, 0)));
        }
        return summaries;
    }

    // ----- helpers ------------------------------------------------------

    /**
     * All-time paid amount per invoice, but only for the invoices in the given
     * list (so an invoice issued inside the range still counts payments made
     * outside it when computing its balance).
     */
    private Map<String, BigDecimal> allTimePaidByInvoice(
            List<Invoice> invoices) {

        Collection<String> ids = invoices.stream()
                .map(Invoice::getId)
                .filter(Objects::nonNull)
                .toList();
        if (ids.isEmpty()) {
            return Map.of();
        }

        Map<String, BigDecimal> map = new HashMap<>();
        for (Payment payment : paymentRepository.findByInvoiceIdIn(ids)) {
            if (payment.getInvoiceId() != null) {
                map.merge(payment.getInvoiceId(),
                        amount(payment.getAmount()), BigDecimal::add);
            }
        }
        map.replaceAll((key, value) -> InvoiceService.round(value));
        return map;
    }

    private static BigDecimal amount(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static <T> List<T> filterByDate(
            List<T> items,
            java.util.function.Function<T, LocalDate> dateOf,
            LocalDate from,
            LocalDate to) {

        List<T> filtered = new ArrayList<>();
        for (T item : items) {
            LocalDate date = dateOf.apply(item);
            if (date == null) {
                continue;
            }
            if (from != null && date.isBefore(from)) {
                continue;
            }
            if (to != null && date.isAfter(to)) {
                continue;
            }
            filtered.add(item);
        }
        return filtered;
    }
}
