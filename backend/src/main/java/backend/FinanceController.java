package backend;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Map;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Authenticated Finance reporting API.
 *
 * All figures are computed live from the invoice, payment and expense
 * collections (see {@link FinanceService}). An optional inclusive date range
 * filters by issue / payment / expense date respectively.
 */
@RestController
@RequestMapping("/api/finance")
public class FinanceController {

    private final FinanceService financeService;

    public FinanceController(FinanceService financeService) {
        this.financeService = financeService;
    }

    @GetMapping("/summary")
    public ResponseEntity<Map<String, Object>> summary(
            @RequestParam(name = "from", required = false) LocalDate from,
            @RequestParam(name = "to", required = false) LocalDate to) {

        return ResponseEntity.ok(Map.of(
                "status", "SUCCESS",
                "summary", financeService.summary(from, to)));
    }

    /** Excel-compatible CSV with the headline figures and the monthly breakdown. */
    @GetMapping(value = "/export", produces = "text/csv")
    public ResponseEntity<byte[]> export(
            @RequestParam(name = "from", required = false) LocalDate from,
            @RequestParam(name = "to", required = false) LocalDate to) {

        FinancialSummaryResponse summary = financeService.summary(from, to);

        StringBuilder csv = new StringBuilder("\uFEFF");
        csv.append(Csv.row("PriyoniX One — Financial Report"));
        csv.append(Csv.row("From",
                from == null ? "All time" : from.toString()));
        csv.append(Csv.row("To", to == null ? "All time" : to.toString()));
        csv.append(Csv.row());

        csv.append(Csv.row("Metric", "Value"));
        csv.append(Csv.row("Total invoiced revenue (issued)",
                summary.totalInvoiced()));
        csv.append(Csv.row("Collected payments", summary.totalCollected()));
        csv.append(Csv.row("Outstanding balance", summary.totalOutstanding()));
        csv.append(Csv.row("Issued invoices", summary.invoiceCount()));
        csv.append(Csv.row("Draft invoices", summary.draftInvoiceCount()));
        csv.append(Csv.row("Paid invoices", summary.paidInvoiceCount()));
        csv.append(Csv.row("Overdue invoices", summary.overdueInvoiceCount()));
        csv.append(Csv.row("Overdue amount", summary.overdueAmount()));
        csv.append(Csv.row("Approved expenses",
                summary.approvedExpenseTotal()));
        csv.append(Csv.row("Pending expenses", summary.pendingExpenseTotal()));
        csv.append(Csv.row("Net profit", summary.netProfit()));
        csv.append(Csv.row("Profit definition", summary.profitDefinition()));
        csv.append(Csv.row());

        csv.append(Csv.row("Month", "Invoiced", "Collected",
                "Approved Expenses", "Profit", "Invoices", "Payments"));
        for (MonthlySummary month : summary.monthly()) {
            csv.append(Csv.row(
                    month.month(),
                    month.invoiced(),
                    month.collected(),
                    month.expenses(),
                    month.profit(),
                    month.invoiceCount(),
                    month.paymentCount()));
        }

        return csvResponse(csv.toString(), "finance-report.csv");
    }

    private static ResponseEntity<byte[]> csvResponse(
            String csv, String filename) {
        byte[] body = csv.getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType(
                        "text/csv; charset=UTF-8"))
                .body(body);
    }
}
