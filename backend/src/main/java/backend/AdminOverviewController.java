
package backend;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Administrative overview endpoint.
 *
 * Restricted to the ADMIN role by the security configuration; an EMPLOYEE
 * receives 403 Forbidden.
 */
@RestController
@RequestMapping("/api/admin")
public class AdminOverviewController {

    private final MetricsService metricsService;

    public AdminOverviewController(MetricsService metricsService) {
        this.metricsService = metricsService;
    }

    @GetMapping("/overview")
    public ResponseEntity<Map<String, Object>> overview(
            Authentication authentication) {

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "SUCCESS");
        body.put("role", UserRoles.ADMIN);
        body.put("email", authentication.getName());
        body.put("metrics", metricsService.metrics());

        return ResponseEntity.ok(body);
    }
}
