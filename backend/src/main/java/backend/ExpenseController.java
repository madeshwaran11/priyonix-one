package backend;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Authenticated Expense REST API.
 *
 * Access rules live in {@link SecurityConfig}: every route requires a valid
 * JWT; DELETE and the status decision (approve / reject / reimburse) require
 * the ADMIN role.
 */
@RestController
@RequestMapping("/api/expenses")
public class ExpenseController {

    private final ExpenseService expenseService;

    public ExpenseController(ExpenseService expenseService) {
        this.expenseService = expenseService;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> list(
            @RequestParam(name = "search", required = false) String search,
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "category", required = false) String category,
            @RequestParam(name = "from", required = false) LocalDate from,
            @RequestParam(name = "to", required = false) LocalDate to,
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "size", defaultValue = "20") int size) {

        PagedResult<ExpenseResponse> result = expenseService.list(
                search, status, category, from, to, page, size);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "SUCCESS");
        body.put("count", result.items().size());
        body.put("total", result.total());
        body.put("page", result.page());
        body.put("size", result.size());
        body.put("totalPages", result.totalPages());
        body.put("expenses", result.items());

        return ResponseEntity.ok(body);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getById(@PathVariable String id) {
        return ResponseEntity.ok(Map.of(
                "status", "SUCCESS",
                "expense", expenseService.getById(id)));
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> create(
            @Valid @RequestBody ExpenseRequest request,
            Authentication authentication) {

        ExpenseResponse created =
                expenseService.create(request, authentication.getName());

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "status", "SUCCESS",
                "message", "Expense created",
                "expense", created));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Map<String, Object>> update(
            @PathVariable String id,
            @Valid @RequestBody ExpenseRequest request) {

        return ResponseEntity.ok(Map.of(
                "status", "SUCCESS",
                "message", "Expense updated",
                "expense", expenseService.update(id, request)));
    }

    /** Administrative approval decision — ADMIN role enforced in SecurityConfig. */
    @PatchMapping("/{id}/status")
    public ResponseEntity<Map<String, Object>> updateStatus(
            @PathVariable String id,
            @Valid @RequestBody ExpenseStatusRequest request,
            Authentication authentication) {

        return ResponseEntity.ok(Map.of(
                "status", "SUCCESS",
                "message", "Expense status updated",
                "expense", expenseService.updateStatus(
                        id, request.status(), authentication.getName())));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> delete(@PathVariable String id) {
        expenseService.delete(id);

        return ResponseEntity.ok(Map.of(
                "status", "SUCCESS",
                "message", "Expense deleted"));
    }
}
