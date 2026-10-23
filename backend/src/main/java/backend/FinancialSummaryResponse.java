package backend;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Financial dashboard / report figures, all computed live from the invoice,
 * payment and expense collections.
 *
 * Definitions are deliberately explicit to avoid double-counting:
 * <ul>
 *   <li>{@code totalInvoiced} — sum of issued (non-DRAFT) invoice totals in the
 *       range, by issue date (revenue recognised).</li>
 *   <li>{@code totalCollected} — sum of payments in the range, by payment date
 *       (cash received). Payments are counted exactly once.</li>
 *   <li>{@code totalOutstanding} — sum of the remaining balance on issued
 *       invoices in the range, using all-time payments applied to them.</li>
 *   <li>{@code approvedExpenseTotal} — sum of APPROVED and REIMBURSED expenses
 *       in the range, by expense date.</li>
 *   <li>{@code netProfit} — {@code totalCollected − approvedExpenseTotal}
 *       (cash basis).</li>
 * </ul>
 */
public record FinancialSummaryResponse(
        LocalDate from,
        LocalDate to,
        BigDecimal totalInvoiced,
        BigDecimal totalCollected,
        BigDecimal totalOutstanding,
        int invoiceCount,
        int draftInvoiceCount,
        BigDecimal draftInvoiceTotal,
        int paidInvoiceCount,
        int overdueInvoiceCount,
        BigDecimal overdueAmount,
        int expenseCount,
        int pendingExpenseCount,
        BigDecimal approvedExpenseTotal,
        BigDecimal pendingExpenseTotal,
        BigDecimal netProfit,
        String profitDefinition,
        List<MonthlySummary> monthly) {
}
