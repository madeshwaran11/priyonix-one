package backend;

import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/platform")
public class PlatformController {
    private final PlatformService service;
    private final AuditService audit;
    private final PlatformSearchService search;

    public PlatformController(PlatformService service, AuditService audit, PlatformSearchService search) {
        this.service = service; this.audit = audit; this.search = search;
    }
    @GetMapping("/organization")
    public PlatformService.Organization organization() { return service.organization(); }
    @PutMapping("/organization")
    public PlatformService.Organization updateOrganization(@RequestBody PlatformService.Organization body, Authentication auth) {
        return service.updateOrganization(body, auth);
    }
    @GetMapping("/users")
    public List<PlatformService.UserView> users(Authentication auth) { return service.users(auth); }
    @PostMapping("/users") @ResponseStatus(HttpStatus.CREATED)
    public PlatformService.UserView createUser(@RequestBody PlatformService.CreateUser body, Authentication auth) {
        return service.createUser(body, auth);
    }
    @PatchMapping("/users/{id}")
    public PlatformService.UserView updateUser(@PathVariable String id, @RequestBody PlatformService.UpdateUser body, Authentication auth) {
        return service.updateUser(id, body, auth);
    }
    @GetMapping("/permissions")
    public List<PlatformService.RolePermissions> permissions() { return service.permissions(); }
    @GetMapping("/audit")
    public PagedResult<AuditService.Event> audit(@RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size, @RequestParam(defaultValue = "") String search, Authentication auth) {
        PlatformService.requireAdmin(auth); return audit.list(page, size, search);
    }
    @GetMapping("/notifications")
    public List<PlatformService.Notification> notifications(Authentication auth) { return service.notifications(auth); }
    @PatchMapping("/notifications/{id}/read")
    public PlatformService.Notification read(@PathVariable String id, Authentication auth) { return service.readNotification(id, auth); }
    @GetMapping("/search")
    public List<PlatformSearchService.Result> search(@RequestParam(defaultValue = "") String q, Authentication auth) {
        return search.search(q, auth);
    }
    @GetMapping("/status")
    public Map<String, Object> status(Authentication auth) { return service.status(auth); }
}
