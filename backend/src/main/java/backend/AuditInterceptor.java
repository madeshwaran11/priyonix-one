package backend;

import java.util.Set;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/** Records metadata only, after a successful mutation. Request/response bodies and headers are never logged. */
@Component
public class AuditInterceptor implements HandlerInterceptor {
    private static final Logger log = LoggerFactory.getLogger(AuditInterceptor.class);
    private static final Set<String> MUTATIONS = Set.of("POST", "PUT", "PATCH", "DELETE");
    private final AuditService audit;
    private final PlatformService platform;
    private final MongoTemplate mongo;
    private final UserRepository users;

    public AuditInterceptor(AuditService audit, PlatformService platform, MongoTemplate mongo, UserRepository users) {
        this.audit = audit; this.platform = platform; this.mongo = mongo; this.users = users;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception error) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getName())
                || error != null || response.getStatus() < 200 || response.getStatus() >= 300
                || !MUTATIONS.contains(request.getMethod())) return;
        String path = request.getRequestURI();
        if (!path.startsWith("/api/") || path.startsWith("/api/auth/")) return;
        String action = switch (request.getMethod()) {
            case "POST" -> "CREATE"; case "DELETE" -> "DELETE"; default -> "UPDATE";
        };
        // Only URI path, never query parameters (which may contain search terms or tokens).
        String resource = path;
        Object createdId = request.getAttribute("audit.resourceId");
        if (createdId instanceof String id && request.getMethod().equals("POST") && !path.endsWith("/" + id))
            resource += "/" + id;
        try { audit.append(auth.getName(), action, resource); }
        catch (RuntimeException ex) {
            // Business changes have already committed. Surface the operational fault without logging payloads.
            log.error("Audit persistence failed for a committed {} operation", request.getMethod());
        }
        if (path.startsWith("/api/platform/notifications/")) return;
        try {
            String[] parts = path.split("/");
            String module = parts.length > 3 && "workspace".equals(parts[2]) ? parts[3]
                    : parts.length > 2 ? parts[2] : "record";
            platform.notifyUser(auth.getName(), "Changes saved", action + " · " + module.replace('-', ' '));
            if ("workspace".equals(parts[2]) && parts.length >= 5) {
                Document record = mongo.findOne(AttachmentService.idQuery(parts[4]), Document.class, "workspace_records");
                if (record != null && record.getString("createdBy") != null
                        && !record.getString("createdBy").equalsIgnoreCase(auth.getName()))
                    platform.notifyUser(record.getString("createdBy"), "Request updated",
                            module.replace('-', ' ') + " · " + record.getString("status"));
            }
            if (("tasks".equals(parts[2]) || "follow-ups".equals(parts[2])) && !"DELETE".equals(request.getMethod())) {
                String recordId = parts.length >= 4 ? parts[3] : createdId instanceof String id ? id : null;
                if (recordId != null) {
                    String collection = "tasks".equals(parts[2]) ? "tasks" : "followups";
                    String assigneeField = "tasks".equals(parts[2]) ? "assigneeId" : "assignedToId";
                    Document record = mongo.findOne(AttachmentService.idQuery(recordId), Document.class, collection);
                    if (record != null && record.get(assigneeField) != null) {
                        Document employee = mongo.findOne(AttachmentService.idQuery(record.get(assigneeField).toString()), Document.class, "employees");
                        if (employee != null && !auth.getName().equalsIgnoreCase(employee.getString("workEmail")))
                            platform.notifyUser(employee.getString("workEmail"), "Assigned work updated", module.replace('-', ' ') + " · " + recordId);
                    }
                }
            }
            if (!PlatformService.isAdmin(auth) && path.startsWith("/api/workspace/")) {
                users.findAll().stream().filter(User::isEnabled).filter(u -> UserRoles.isAdmin(u.getRole()))
                        .forEach(u -> platform.notifyUser(u.getEmail(), "Team activity",
                                auth.getName() + " updated " + module.replace('-', ' ')));
            }
        } catch (RuntimeException ex) { log.warn("Workflow notification persistence failed"); }
    }
}
