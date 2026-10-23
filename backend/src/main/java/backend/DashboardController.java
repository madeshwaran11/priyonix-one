
package backend;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.core.Authentication;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final MetricsService metricsService;

    public DashboardController(MetricsService metricsService) {
        this.metricsService = metricsService;
    }

    @GetMapping
    public Map<String, String> dashboard(Authentication authentication) {
        return Map.of(
            "status", "SUCCESS",
            "message", "Authenticated dashboard access granted",
            "email", authentication.getName()
        );
    }

    /**
     * Real, live counts for the overview page. All values come from the
     * projects/tasks collections.
     */
    @GetMapping("/metrics")
    public Map<String, Object> metrics() {
        return Map.of(
            "status", "SUCCESS",
            "metrics", metricsService.metrics()
        );
    }
}
