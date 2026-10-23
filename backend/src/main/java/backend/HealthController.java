
package backend;

import java.util.Map;

import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public health probe.
 *
 * CORS is handled globally by {@link SecurityConfig} so the allowed origins
 * stay configurable through {@code CORS_ALLOWED_ORIGINS} instead of being
 * hardcoded per controller.
 */
@RestController
public class HealthController {

    private final MongoTemplate mongoTemplate;

    public HealthController(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @GetMapping("/api/health")
    public Map<String, String> health() {
        mongoTemplate.getDb().runCommand(
            new org.bson.Document("ping", 1)
        );

        return Map.of(
            "status", "UP",
            "application", "PriyoniX One",
            "database", "connected"
        );
    }
}
