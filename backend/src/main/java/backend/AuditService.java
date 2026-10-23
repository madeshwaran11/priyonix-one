package backend;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;
import org.bson.Document;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

/** Append-only audit store: the application deliberately exposes no update or deletion operation. */
@Service
public class AuditService {
    private final MongoTemplate mongo;
    public AuditService(MongoTemplate mongo) { this.mongo = mongo; }
    public record Event(String id, String actor, String action, String resource, Instant createdAt) {}

    public void append(String actor, String action, String resource) {
        mongo.insert(new Document("_id", UUID.randomUUID().toString()).append("actor", actor)
                .append("action", action).append("resource", resource)
                .append("createdAt", Date.from(Instant.now())), "audit_events");
    }

    public PagedResult<Event> list(int page, int size, String search) {
        int safePage = Math.max(page, 1), safeSize = Math.max(1, Math.min(size, 100));
        Query query = new Query();
        if (search != null && !search.isBlank()) {
            Pattern term = Pattern.compile(Pattern.quote(PlatformService.optional(search, 160)), Pattern.CASE_INSENSITIVE);
            query.addCriteria(new Criteria().orOperator(Criteria.where("actor").regex(term),
                    Criteria.where("action").regex(term), Criteria.where("resource").regex(term)));
        }
        long total = mongo.count(query, "audit_events");
        query.with(Sort.by(Sort.Direction.DESC, "createdAt")).skip((long) (safePage - 1) * safeSize).limit(safeSize);
        List<Event> items = mongo.find(query, Document.class, "audit_events").stream()
                .map(d -> new Event(d.getString("_id"), d.getString("actor"), d.getString("action"),
                        d.getString("resource"), d.getDate("createdAt").toInstant())).toList();
        return new PagedResult<>(items, safePage, safeSize, total, (int) Math.ceil((double) total / safeSize));
    }
}
