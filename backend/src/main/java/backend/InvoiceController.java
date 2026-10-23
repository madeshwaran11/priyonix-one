package backend;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import jakarta.validation.Valid;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Authenticated Invoice REST API.
 *
 * Access rules live in {@link SecurityConfig}: every route requires a valid
 * JWT and DELETE additionally requires the ADMIN role. The createdBy/recordedBy
 * audit fields always come from the authenticated principal, never from the
 * request body.
 */
@RestController
@RequestMapping("/api/invoices")
public class InvoiceController {

    private final InvoiceService invoiceService;
    private final PaymentService paymentService;

    public InvoiceController(
            InvoiceService invoiceService,
            PaymentService paymentService) {
        this.invoiceService = invoiceService;
        this.paymentService = paymentService;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> list(
            @RequestParam(name = "search", required = false) String search,
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "clientId", required = false) String clientId,
            @RequestParam(name = "projectId", required = false) String projectId,
            @RequestParam(name = "overdue", defaultValue = "false")
            boolean overdue,
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "size", defaultValue = "20") int size) {

        PagedResult<InvoiceResponse> result = invoiceService.list(
                search, status, clientId, projectId, overdue, page, size);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "SUCCESS");
        body.put("count", result.items().size());
        body.put("total", result.total());
        body.put("page", result.page());
        body.put("size", result.size());
        body.put("totalPages", result.totalPages());
        body.put("invoices", result.items());

        return ResponseEntity.ok(body);
    }

    /** Excel-compatible CSV of every matching invoice (no pagination). */
    @GetMapping(value = "/export", produces = "text/csv")
    public ResponseEntity<byte[]> export(
            @RequestParam(name = "search", required = false) String search,
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "clientId", required = false) String clientId,
            @RequestParam(name = "projectId", required = false) String projectId,
            @RequestParam(name = "overdue", defaultValue = "false")
            boolean overdue) {

        List<InvoiceResponse> invoices = invoiceService.listAll(
                search, status, clientId, projectId, overdue);

        StringBuilder csv = new StringBuilder("\uFEFF");
        csv.append(Csv.row(
                "Invoice Number", "Client", "Project", "Issue Date", "Due Date",
                "Status", "Subtotal", "Discount", "Tax Rate (%)", "Tax",
                "Total", "Amount Paid", "Balance", "Payments", "Created By"));

        for (InvoiceResponse invoice : invoices) {
            csv.append(Csv.row(
                    invoice.invoiceNumber(),
                    invoice.clientName(),
                    projectLabel(invoice),
                    invoice.issueDate(),
                    invoice.dueDate(),
                    invoice.status(),
                    invoice.subtotal(),
                    invoice.discount(),
                    invoice.taxRate(),
                    invoice.taxAmount(),
                    invoice.total(),
                    invoice.amountPaid(),
                    invoice.balance(),
                    invoice.paymentCount(),
                    invoice.createdBy()));
        }

        return csvResponse(csv.toString(), "invoices.csv");
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getById(@PathVariable String id) {
        return ResponseEntity.ok(Map.of(
                "status", "SUCCESS",
                "invoice", invoiceService.getById(id)));
    }

    @GetMapping("/{id}/payments")
    public ResponseEntity<Map<String, Object>> payments(
            @PathVariable String id) {

        List<PaymentResponse> payments = paymentService.listByInvoice(id);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "SUCCESS");
        body.put("count", payments.size());
        body.put("payments", payments);

        return ResponseEntity.ok(body);
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> create(
            @Valid @RequestBody InvoiceRequest request,
            Authentication authentication) {

        InvoiceResponse created =
                invoiceService.create(request, authentication.getName());

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "status", "SUCCESS",
                "message", "Invoice created",
                "invoice", created));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Map<String, Object>> update(
            @PathVariable String id,
            @Valid @RequestBody InvoiceRequest request) {

        return ResponseEntity.ok(Map.of(
                "status", "SUCCESS",
                "message", "Invoice updated",
                "invoice", invoiceService.update(id, request)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> delete(
            @PathVariable String id,
            @RequestParam(name = "cascade", defaultValue = "false")
            boolean cascade) {

        invoiceService.delete(id, cascade);

        return ResponseEntity.ok(Map.of(
                "status", "SUCCESS",
                "message", cascade
                        ? "Invoice and its payments deleted"
                        : "Invoice deleted"));
    }

    private String projectLabel(InvoiceResponse invoice) {
        if (invoice.projectName() == null) {
            return invoice.projectCode() == null ? "" : invoice.projectCode();
        }
        if (invoice.projectCode() == null) {
            return invoice.projectName();
        }
        return invoice.projectName() + " (" + invoice.projectCode() + ")";
    }

    static ResponseEntity<byte[]> csvResponse(String csv, String filename) {
        byte[] body = csv.getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType(
                        "text/csv; charset=UTF-8"))
                .body(body);
    }
}
