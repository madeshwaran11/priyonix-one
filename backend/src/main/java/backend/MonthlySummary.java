package backend;

import java.math.BigDecimal;

/**
 * One calendar month of the financial report.
 *
 * {@code invoiced} is revenue recognised by issue date, {@code collected} is
 * cash received by payment date and {@code expenses} is approved spend by
 * expense date. These three series are derived from separate collections and
 * therefore never double-count a single record.
 */
public record MonthlySummary(
        String month,
        BigDecimal invoiced,
        BigDecimal collected,
        BigDecimal expenses,
        BigDecimal profit,
        int invoiceCount,
        int paymentCount) {
}
