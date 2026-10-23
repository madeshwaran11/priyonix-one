package backend;

import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Currency;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.bson.Document;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Organization and account lifecycle APIs. No entity containing a credential leaves this service. */
@Service
public class PlatformService {
    private final MongoTemplate mongo;
    private final UserRepository users;
    private final PasswordEncoder passwords;
    private final RefreshTokenService tokens;

    public PlatformService(MongoTemplate mongo, UserRepository users,
            PasswordEncoder passwords, RefreshTokenService tokens) {
        this.mongo = mongo;
        this.users = users;
        this.passwords = passwords;
        this.tokens = tokens;
    }

    public record Organization(String companyName, String legalName, String email,
            String phone, String address, String currency, String timezone, int fiscalYearStart) {}
    public record UserView(String id, String email, String role, boolean enabled) {}
    public record CreateUser(String email, String password, String role) {}
    public record UpdateUser(String role, Boolean enabled) {}
    public record RolePermissions(String role, List<String> permissions) {}
    public record Notification(String id, String title, String message, boolean read, Instant createdAt) {}

    public static boolean isAdmin(Authentication auth) {
        return auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
    }

    public static void requireAdmin(Authentication auth) {
        if (!isAdmin(auth)) throw new AccessDeniedException("Administrator access is required");
    }

    public Organization organization() {
        Document doc = mongo.findById("organization", Document.class, "platform_settings");
        if (doc == null) return new Organization("PriyoniX One", "PriyoniX Global Ventures Private Limited",
                "", "", "", "INR", "Asia/Kolkata", 4);
        return new Organization(doc.getString("companyName"), doc.getString("legalName"),
                doc.getString("email"), doc.getString("phone"), doc.getString("address"),
                doc.getString("currency"), doc.getString("timezone"), doc.getInteger("fiscalYearStart", 4));
    }

    public Organization updateOrganization(Organization input, Authentication auth) {
        requireAdmin(auth);
        String company = required(input.companyName(), "Company name", 160);
        String legal = required(input.legalName(), "Legal name", 200);
        String currency = required(input.currency(), "Currency", 3).toUpperCase(Locale.ROOT);
        String timezone = required(input.timezone(), "Timezone", 80);
        try { Currency.getInstance(currency); }
        catch (IllegalArgumentException ex) { throw new InvalidRequestException("Use a valid ISO 4217 currency code"); }
        try { ZoneId.of(timezone); }
        catch (RuntimeException ex) { throw new InvalidRequestException("Use a valid IANA timezone"); }
        if (input.fiscalYearStart() < 1 || input.fiscalYearStart() > 12)
            throw new InvalidRequestException("Fiscal year start must be a month from 1 to 12");
        String email = optional(input.email(), 254).toLowerCase(Locale.ROOT);
        if (!email.isBlank()) validateEmail(email);
        Organization result = new Organization(company, legal, email,
                optional(input.phone(), 40), optional(input.address(), 1000), currency, timezone, input.fiscalYearStart());
        mongo.save(new Document("_id", "organization").append("companyName", result.companyName())
                .append("legalName", result.legalName()).append("email", result.email())
                .append("phone", result.phone()).append("address", result.address())
                .append("currency", result.currency()).append("timezone", result.timezone())
                .append("fiscalYearStart", result.fiscalYearStart()), "platform_settings");
        return result;
    }

    public List<UserView> users(Authentication auth) {
        requireAdmin(auth);
        return users.findAll().stream().map(PlatformService::view)
                .sorted(java.util.Comparator.comparing(UserView::email)).toList();
    }

    @Transactional("workspaceTransactionManager")
    public synchronized UserView createUser(CreateUser input, Authentication auth) {
        requireAdmin(auth);
        lockAccountAdministration();
        String email = required(input.email(), "Email", 254).toLowerCase(Locale.ROOT);
        validateEmail(email);
        String role = UserRoles.parseRequestedRole(input.role());
        if (role == null) throw new InvalidRequestException("Role must be ADMIN or EMPLOYEE");
        List<String> violations = PasswordPolicy.violations(input.password());
        if (!violations.isEmpty()) throw new InvalidRequestException(String.join(" ", violations));
        // BCrypt truncates beyond 72 bytes; explicitly reject instead of silently weakening the password.
        if (input.password().getBytes(StandardCharsets.UTF_8).length > 72)
            throw new InvalidRequestException("Password must be at most 72 UTF-8 bytes");
        if (users.existsByEmailIgnoreCase(email)) throw new DuplicateResourceException("An account already uses this email");
        User user = new User();
        user.setEmail(email); user.setRole(role); user.setEnabled(true);
        user.setPassword(passwords.encode(input.password()));
        return view(users.save(user));
    }

    @Transactional("workspaceTransactionManager")
    public synchronized UserView updateUser(String id, UpdateUser input, Authentication auth) {
        requireAdmin(auth);
        lockAccountAdministration();
        User user = users.findById(id).orElseThrow(() -> new ResourceNotFoundException("Account not found"));
        String role = input.role() == null ? UserRoles.canonical(user.getRole()) : UserRoles.parseRequestedRole(input.role());
        if (role == null) throw new InvalidRequestException("Role must be ADMIN or EMPLOYEE");
        boolean enabled = input.enabled() == null ? user.isEnabled() : input.enabled();
        boolean removesAdmin = user.isEnabled() && UserRoles.isAdmin(user.getRole())
                && (!enabled || !UserRoles.ADMIN.equals(role));
        if (user.getEmail().equalsIgnoreCase(auth.getName()) && (!enabled || !UserRoles.ADMIN.equals(role)))
            throw new ConflictException("You cannot disable or demote your own administrator account");
        if (removesAdmin && users.findAll().stream().filter(User::isEnabled)
                .filter(u -> UserRoles.isAdmin(u.getRole())).count() <= 1)
            throw new ConflictException("At least one enabled administrator must remain");
        user.setRole(role); user.setEnabled(enabled);
        UserView result = view(users.save(user));
        tokens.revokeAllForUser(user.getId());
        return result;
    }

    private static UserView view(User user) {
        return new UserView(user.getId(), user.getEmail(), UserRoles.canonical(user.getRole()), user.isEnabled());
    }

    private void lockAccountAdministration() {
        // Shared document forces concurrent transactions on different server instances to conflict,
        // so two administrators cannot simultaneously disable/demote each other.
        mongo.updateFirst(Query.query(Criteria.where("_id").is("account-administration")),
                new Update().inc("revision", 1L), "platform_locks");
    }

    public List<RolePermissions> permissions() {
        return List.of(new RolePermissions("ADMIN", List.of("Organization settings and user administration",
                "All business modules and reports", "Approve requests and manage financial records",
                "Audit log and operational status", "All record attachments")),
                new RolePermissions("EMPLOYEE", List.of("Shared CRM and project collaboration",
                "Own attendance, leave requests, timesheets and purchase requests", "Own profile and assigned work summary",
                "Own notifications", "Attachments for accessible records; delete own uploads")));
    }

    public List<Notification> notifications(Authentication auth) {
        Query q = Query.query(Criteria.where("recipient").is(auth.getName()))
                .with(Sort.by(Sort.Direction.DESC, "createdAt")).limit(100);
        return mongo.find(q, Document.class, "platform_notifications").stream().map(PlatformService::notification).toList();
    }

    public Notification readNotification(String id, Authentication auth) {
        Query q = Query.query(Criteria.where("_id").is(id).and("recipient").is(auth.getName()));
        Document doc = mongo.findOne(q, Document.class, "platform_notifications");
        if (doc == null) throw new ResourceNotFoundException("Notification not found");
        mongo.updateFirst(q, Update.update("read", true), "platform_notifications");
        doc.put("read", true);
        return notification(doc);
    }

    public void notifyUser(String recipient, String title, String message) {
        if (recipient == null || recipient.isBlank()) return;
        mongo.insert(new Document("_id", UUID.randomUUID().toString())
                .append("recipient", recipient).append("title", title).append("message", message)
                .append("read", false).append("createdAt", java.util.Date.from(Instant.now())), "platform_notifications");
    }

    private static Notification notification(Document doc) {
        return new Notification(doc.getString("_id"), doc.getString("title"), doc.getString("message"),
                doc.getBoolean("read", false), doc.getDate("createdAt").toInstant());
    }

    public Map<String, Object> status(Authentication auth) {
        requireAdmin(auth);
        mongo.executeCommand(new Document("ping", 1));
        Map<String, Long> counts = new LinkedHashMap<>();
        for (String collection : List.of("users", "employees", "projects", "tasks", "invoices"))
            counts.put(collection, mongo.count(new Query(), collection));
        counts.put("workspaceRecords", mongo.count(new Query(), "workspace_records"));
        counts.put("attachments", mongo.count(new Query(), "platform_attachments"));
        counts.put("auditEvents", mongo.count(new Query(), "audit_events"));
        return Map.of("status", "UP", "database", "UP", "uptimeSeconds",
                ManagementFactory.getRuntimeMXBean().getUptime() / 1000, "counts", counts);
    }

    static String required(String value, String label, int max) {
        String clean = optional(value, max);
        if (clean.isBlank()) throw new InvalidRequestException(label + " is required");
        return clean;
    }

    static String optional(String value, int max) {
        String clean = value == null ? "" : value.trim();
        if (clean.length() > max) throw new InvalidRequestException("Text must contain at most " + max + " characters");
        return clean;
    }

    private static void validateEmail(String email) {
        if (!email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) throw new InvalidRequestException("A valid email address is required");
    }
}
