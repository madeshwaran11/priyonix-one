package backend;

import java.util.LinkedHashMap;
import java.util.Map;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
 * Authenticated CRM Lead REST API.
 *
 * Access rules live in {@link SecurityConfig}: every route requires a valid
 * JWT and DELETE additionally requires the ADMIN role.
 */
@RestController
@RequestMapping("/api/leads")
public class LeadController {

    private final LeadService leadService;
    private final FollowUpService followUpService;

    public LeadController(
            LeadService leadService,
            FollowUpService followUpService) {
        this.leadService = leadService;
        this.followUpService = followUpService;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> list(
            @RequestParam(name = "search", required = false) String search,
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "priority", required = false) String priority,
            @RequestParam(name = "assignedToId", required = false)
            String assignedToId,
            @RequestParam(name = "campaignId", required = false)
            String campaignId,
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "size", defaultValue = "20") int size) {

        PagedResult<LeadResponse> result = leadService.list(
                search, status, priority, assignedToId, campaignId, page, size);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "SUCCESS");
        body.put("count", result.items().size());
        body.put("total", result.total());
        body.put("page", result.page());
        body.put("size", result.size());
        body.put("totalPages", result.totalPages());
        body.put("leads", result.items());

        return ResponseEntity.ok(body);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getById(@PathVariable String id) {
        return ResponseEntity.ok(Map.of(
                "status", "SUCCESS",
                "lead", leadService.getById(id)));
    }

    @GetMapping("/{id}/follow-ups")
    public ResponseEntity<Map<String, Object>> followUps(
            @PathVariable String id) {

        var followUps = followUpService.listByLead(id);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "SUCCESS");
        body.put("count", followUps.size());
        body.put("followUps", followUps);

        return ResponseEntity.ok(body);
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> create(
            @Valid @RequestBody LeadRequest request) {

        LeadResponse created = leadService.create(request);

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "status", "SUCCESS",
                "message", "Lead created",
                "lead", created));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Map<String, Object>> update(
            @PathVariable String id,
            @Valid @RequestBody LeadRequest request) {

        return ResponseEntity.ok(Map.of(
                "status", "SUCCESS",
                "message", "Lead updated",
                "lead", leadService.update(id, request)));
    }

    /**
     * Converts a WON lead into a client. Reuses an existing client with the
     * same email instead of creating a duplicate, and refuses a second
     * conversion of the same lead.
     */
    @PostMapping("/{id}/convert")
    public ResponseEntity<Map<String, Object>> convert(
            @PathVariable String id) {

        LeadConversionResponse conversion = leadService.convert(id);

        return ResponseEntity.ok(Map.of(
                "status", "SUCCESS",
                "message", conversion.created()
                        ? "Lead converted and client created"
                        : "Lead linked to the existing client",
                "created", conversion.created(),
                "lead", conversion.lead(),
                "client", conversion.client()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> delete(
            @PathVariable String id,
            @RequestParam(name = "cascade", defaultValue = "false")
            boolean cascade) {

        leadService.delete(id, cascade);

        return ResponseEntity.ok(Map.of(
                "status", "SUCCESS",
                "message", cascade
                        ? "Lead and its follow-ups deleted"
                        : "Lead deleted"));
    }
}
