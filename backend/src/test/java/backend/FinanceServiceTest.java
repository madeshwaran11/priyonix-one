package backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FinanceServiceTest {

    @Mock
    private InvoiceRepository invoiceRepository;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private ExpenseRepository expenseRepository;

    private FinanceService financeService;

    @BeforeEach
    void setUp() {
        financeService = new FinanceService(
                invoiceRepository, paymentRepository, expenseRepository);
    }

    // ----- fixtures ------------------------------------------------------

    private Invoice invoice(
            String id, String status, LocalDate issueDate, LocalDate dueDate,
            String amount) {
        Invoice invoice = new Invoice();
        invoice.setId(id);
        invoice.setInvoiceNumber("INV-" + id);
        invoice.setClientId("client-1");
        invoice.setStatus(status);
        invoice.setIssueDate(issueDate);
        invoice.setDueDate(dueDate);
        InvoiceLineItem item = new InvoiceLineItem();
        item.setDescription("Work");
        item.setQuantity(new BigDecimal("1"));
        item.setUnitPrice(new BigDecimal(amount));
        invoice.setLineItems(List.of(item));
        invoice.setCreatedAt(Instant.parse("2024-01-01T00:00:00Z"));
        return invoice;
    }

    private Payment payment(
            String id, String invoiceId, LocalDate date, String amount) {
        Payment payment = new Payment();
        payment.setId(id);
        payment.setInvoiceId(invoiceId);
        payment.setAmount(new BigDecimal(amount));
        payment.setPaymentDate(date);
        return payment;
    }

    private Expense expense(
            String id, String status, LocalDate date, String amount) {
        Expense expense = new Expense();
        expense.setId(id);
        expense.setTitle("Expense");
        expense.setStatus(status);
        expense.setExpenseDate(date);
        expense.setAmount(new BigDecimal(amount));
        return expense;
    }

    // ----- summary -------------------------------------------------------

    @Test
    void summaryComputesFiguresOnce() {
        LocalDate now = LocalDate.now();

        Invoice sent = invoice("inv-1", "SENT",
                now.minusDays(5), now.plusDays(10), "1000");
        Invoice overdue = invoice("inv-2", "SENT",
                now.minusDays(5), now.minusDays(3), "500");
        Invoice draft = invoice("inv-3", "DRAFT",
                now.minusDays(5), now.plusDays(10), "200");

        Payment collected = payment("pay-1", "inv-1", now.minusDays(2), "400");

        Expense approved = expense("exp-1", "APPROVED", now.minusDays(2), "150");
        Expense pending = expense("exp-2", "PENDING", now.minusDays(2), "50");
        Expense rejected = expense("exp-3", "REJECTED", now.minusDays(2), "25");

        when(invoiceRepository.findAllByOrderByCreatedAtDesc())
                .thenReturn(List.of(sent, overdue, draft));
        when(paymentRepository.findAllByOrderByCreatedAtDesc())
                .thenReturn(List.of(collected));
        when(expenseRepository.findAllByOrderByCreatedAtDesc())
                .thenReturn(List.of(approved, pending, rejected));
        when(paymentRepository.findByInvoiceIdIn(any()))
                .thenReturn(List.of(collected));

        FinancialSummaryResponse summary = financeService.summary(
                now.minusMonths(1), now.plusMonths(1));

        assertEquals(new BigDecimal("1500.00"), summary.totalInvoiced());
        assertEquals(new BigDecimal("400.00"), summary.totalCollected());
        assertEquals(new BigDecimal("1100.00"), summary.totalOutstanding());
        assertEquals(2, summary.invoiceCount());
        assertEquals(1, summary.draftInvoiceCount());
        assertEquals(new BigDecimal("200.00"), summary.draftInvoiceTotal());
        assertEquals(0, summary.paidInvoiceCount());
        assertEquals(1, summary.overdueInvoiceCount());
        assertEquals(new BigDecimal("500.00"), summary.overdueAmount());
        assertEquals(3, summary.expenseCount());
        assertEquals(1, summary.pendingExpenseCount());
        assertEquals(new BigDecimal("150.00"), summary.approvedExpenseTotal());
        assertEquals(new BigDecimal("50.00"), summary.pendingExpenseTotal());
        assertEquals(new BigDecimal("250.00"), summary.netProfit());
        assertEquals(FinanceService.PROFIT_DEFINITION,
                summary.profitDefinition());
    }

    @Test
    void summaryUsesAllTimePaymentsForBalanceButRangesCollections() {
        LocalDate now = LocalDate.now();

        Invoice sent = invoice("inv-1", "SENT",
                now.minusDays(2), now.plusDays(10), "1000");
        // Payment made well before the reporting window.
        Payment earlier = payment("pay-1", "inv-1", now.minusDays(30), "400");

        when(invoiceRepository.findAllByOrderByCreatedAtDesc())
                .thenReturn(List.of(sent));
        when(paymentRepository.findAllByOrderByCreatedAtDesc())
                .thenReturn(List.of(earlier));
        when(expenseRepository.findAllByOrderByCreatedAtDesc())
                .thenReturn(List.of());
        when(paymentRepository.findByInvoiceIdIn(any()))
                .thenReturn(List.of(earlier));

        FinancialSummaryResponse summary = financeService.summary(
                now.minusDays(10), now.plusDays(10));

        // Cash collected inside the window is zero ...
        assertEquals(new BigDecimal("0.00"), summary.totalCollected());
        // ... but the balance still reflects the earlier payment.
        assertEquals(new BigDecimal("600.00"), summary.totalOutstanding());
    }

    // ----- monthly -------------------------------------------------------

    @Test
    void monthlyGroupsRecordsByCalendarMonth() {
        LocalDate january = LocalDate.of(2026, 1, 15);
        LocalDate february = LocalDate.of(2026, 2, 10);

        Invoice invJan = invoice("inv-jan", "SENT",
                january, january.plusDays(30), "1000");
        Invoice invFeb = invoice("inv-feb", "SENT",
                february, february.plusDays(30), "500");
        Invoice draft = invoice("inv-draft", "DRAFT",
                february, february.plusDays(30), "999");

        Payment payJan = payment("pay-jan", "inv-jan", january, "400");
        Payment payFeb = payment("pay-feb", "inv-feb", february, "250");

        Expense approvedJan = expense("exp-jan", "APPROVED", january, "150");
        Expense pendingFeb = expense("exp-feb", "PENDING", february, "999");

        List<MonthlySummary> months = financeService.monthly(
                List.of(invJan, invFeb, draft),
                List.of(payJan, payFeb),
                List.of(approvedJan, pendingFeb));

        assertEquals(2, months.size());

        MonthlySummary first = months.get(0);
        assertEquals("2026-01", first.month());
        assertEquals(new BigDecimal("1000.00"), first.invoiced());
        assertEquals(new BigDecimal("400.00"), first.collected());
        assertEquals(new BigDecimal("150.00"), first.expenses());
        assertEquals(new BigDecimal("250.00"), first.profit());
        assertEquals(1, first.invoiceCount());
        assertEquals(1, first.paymentCount());

        MonthlySummary second = months.get(1);
        assertEquals("2026-02", second.month());
        assertEquals(new BigDecimal("500.00"), second.invoiced());
        assertEquals(new BigDecimal("250.00"), second.collected());
        assertEquals(new BigDecimal("0.00"), second.expenses());
        assertEquals(new BigDecimal("250.00"), second.profit());
        assertEquals(1, second.invoiceCount());
    }
}
