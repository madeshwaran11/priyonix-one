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
 * Authenticated Client REST API.
 *
 * Access rules live in {@link SecurityConfig}: every route requires a valid
 * JWT and DELETE additionally requires the ADMIN role.
 */
@RestController
@RequestMapping("/api/clients")
public class ClientController {

    private final ClientService clientService;
    private final FollowUpService followUpService;

    public ClientController(
            ClientService clientService,
            FollowUpService followUpService) {
        this.clientService = clientService;
        this.followUpService = followUpService;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> list(
            @RequestParam(name = "search", required = false) String search,
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "size", defaultValue = "20") int size) {

        PagedResult<ClientResponse> result =
                clientService.list(search, status, page, size);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "SUCCESS");
        body.put("count", result.items().size());
        body.put("total", result.total());
        body.put("page", result.page());
        body.put("size", result.size());
        body.put("totalPages", result.totalPages());
        body.put("clients", result.items());

        return ResponseEntity.ok(body);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getById(@PathVariable String id) {
        return ResponseEntity.ok(Map.of(
                "status", "SUCCESS",
                "client", clientService.getById(id)));
    }

    @GetMapping("/{id}/follow-ups")
    public ResponseEntity<Map<String, Object>> followUps(
            @PathVariable String id) {

        var followUps = followUpService.listByClient(id);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "SUCCESS");
        body.put("count", followUps.size());
        body.put("followUps", followUps);

        return ResponseEntity.ok(body);
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> create(
            @Valid @RequestBody ClientRequest request) {

        ClientResponse created = clientService.create(request);

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "status", "SUCCESS",
                "message", "Client created",
                "client", created));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Map<String, Object>> update(
            @PathVariable String id,
            @Valid @RequestBody ClientRequest request) {

        return ResponseEntity.ok(Map.of(
                "status", "SUCCESS",
                "message", "Client updated",
                "client", clientService.update(id, request)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> delete(
            @PathVariable String id,
            @RequestParam(name = "cascade", defaultValue = "false")
            boolean cascade) {

        clientService.delete(id, cascade);

        return ResponseEntity.ok(Map.of(
                "status", "SUCCESS",
                "message", cascade
                        ? "Client and its follow-ups deleted"
                        : "Client deleted"));
    }
}
