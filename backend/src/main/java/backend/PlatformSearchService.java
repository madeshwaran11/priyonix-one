package backend;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

/** Searches only approved display fields and applies the same workspace access checks as record endpoints. */
@Service
public class PlatformSearchService {
    private final MongoTemplate mongo;
    private final WorkspaceService workspace;
    public PlatformSearchService(MongoTemplate mongo, WorkspaceService workspace) { this.mongo = mongo; this.workspace = workspace; }
    public record Result(String type, String id, String label, String section) {}
    public List<Result> search(String query, Authentication auth) {
        String term = PlatformService.optional(query, 100);
        if (term.length() < 2) return List.of();
        Pattern pattern = Pattern.compile(Pattern.quote(term), Pattern.CASE_INSENSITIVE);
        List<Result> results = new ArrayList<>();
        Map<String, List<String>> fields = Map.of("clients", List.of("name", "companyName"),
                "leads", List.of("name", "company"), "projects", List.of("name", "projectCode"),
                "tasks", List.of("title", "taskCode"), "employees", List.of("fullName", "employeeId"),
                "invoices", List.of("invoiceNumber"), "campaigns", List.of("name"));
        for (var entry : fields.entrySet()) {
            String collection = entry.getKey();
            if (!PlatformService.isAdmin(auth) && List.of("employees", "invoices").contains(collection)) continue;
            Query q = Query.query(new Criteria().orOperator(entry.getValue().stream()
                    .map(field -> Criteria.where(field).regex(pattern)).toArray(Criteria[]::new))).limit(10);
            q.fields().include("_id"); entry.getValue().forEach(field -> q.fields().include(field));
            for (Document doc : mongo.find(q, Document.class, collection)) {
                String label = entry.getValue().stream().map(doc::getString).filter(s -> s != null && !s.isBlank()).findFirst().orElse(collection);
                results.add(new Result(collection, doc.get("_id").toString(), label, collection));
            }
        }
        List<String> labels = List.of("name", "title", "code", "reference", "studentName", "vendorName", "email", "description", "assetTag", "subject");
        Query q = Query.query(new Criteria().orOperator(labels.stream().map(f -> Criteria.where("values." + f)
                .regex(pattern)).toArray(Criteria[]::new))).limit(200);
        for (WorkspaceRecord record : mongo.find(q, WorkspaceRecord.class, "workspace_records")) {
            if (workspace.canRead(record, auth)) results.add(new Result(record.getModule(), record.getId(), workspace.label(record), record.getModule()));
            if (results.size() >= 100) break;
        }
        return List.copyOf(results);
    }
}
