
package backend;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Employee workspace endpoint.
 *
 * Restricted to the EMPLOYEE role by the security configuration; an ADMIN
 * receives 403 Forbidden. The data is scoped to the signed-in employee: the
 * HR record that matches the account email (if any) plus the tasks and
 * follow-ups assigned to that employee.
 */
@RestController
@RequestMapping("/api/employee")
public class EmployeeOverviewController {

    private final EmployeeRepository employeeRepository;
    private final TaskRepository taskRepository;
    private final FollowUpRepository followUpRepository;

    public EmployeeOverviewController(
            EmployeeRepository employeeRepository,
            TaskRepository taskRepository,
            FollowUpRepository followUpRepository) {
        this.employeeRepository = employeeRepository;
        this.taskRepository = taskRepository;
        this.followUpRepository = followUpRepository;
    }

    @GetMapping("/overview")
    public ResponseEntity<Map<String, Object>> overview(
            Authentication authentication) {

        String email = authentication.getName();

        Optional<Employee> employee = employeeRepository
                .findByWorkEmailIgnoreCase(email);

        long assignedTasks = 0;
        long openTasks = 0;
        long assignedFollowUps = 0;
        long openFollowUps = 0;

        if (employee.isPresent()) {
            String employeeId = employee.get().getId();
            assignedTasks = taskRepository.countByAssigneeId(employeeId);
            openTasks = taskRepository
                    .countByAssigneeIdAndStatusNot(employeeId, "COMPLETED");
            assignedFollowUps = followUpRepository
                    .countByAssignedToId(employeeId);
            openFollowUps = followUpRepository
                    .countByAssignedToIdAndCompletedFalse(employeeId);
        }

        Map<String, Object> profile = new LinkedHashMap<>();
        profile.put("employeeId",
                employee.map(Employee::getEmployeeId).orElse(null));
        profile.put("fullName",
                employee.map(Employee::getFullName).orElse(null));
        profile.put("department",
                employee.map(Employee::getDepartment).orElse(null));
        profile.put("designation",
                employee.map(Employee::getDesignation).orElse(null));
        profile.put("employmentStatus",
                employee.map(Employee::getEmploymentStatus).orElse(null));

        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("assignedTasks", assignedTasks);
        metrics.put("openTasks", openTasks);
        metrics.put("assignedFollowUps", assignedFollowUps);
        metrics.put("openFollowUps", openFollowUps);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "SUCCESS");
        body.put("role", UserRoles.EMPLOYEE);
        body.put("email", email);
        body.put("profile", profile);
        body.put("metrics", metrics);

        return ResponseEntity.ok(body);
    }
}
