package backend;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.springframework.stereotype.Service;

/**
 * Business rules for employee records.
 *
 * Responsibilities:
 *  - normalize work emails (trim + lower-case) so duplicates can never be
 *    stored with different casing;
 *  - normalize employee IDs (trim) and reject duplicates case-insensitively;
 *  - enforce the allowed employment status values;
 *  - stamp the creation timestamp exactly once, on create.
 */
@Service
public class EmployeeService {
    @org.springframework.beans.factory.annotation.Autowired
    private LegacyIntegrityService integrity;


    /** Employment statuses accepted by the API. */
    public static final Set<String> ALLOWED_STATUSES = Set.of(
            "ACTIVE", "PROBATION", "ON_LEAVE", "RESIGNED", "TERMINATED");

    public static final String DEFAULT_STATUS = "ACTIVE";

    private final EmployeeRepository employeeRepository;

    public EmployeeService(EmployeeRepository employeeRepository) {
        this.employeeRepository = employeeRepository;
    }

    public List<Employee> list(String search) {
        List<Employee> employees =
                employeeRepository.findAllByOrderByFullNameAsc();

        if (search == null || search.isBlank()) {
            return employees;
        }

        String needle = search.trim().toLowerCase(Locale.ROOT);

        // Small, in-memory filter keeps the query simple and predictable.
        // Replace with a Mongo text/regex query if the collection grows.
        return employees.stream()
                .filter(employee -> matches(employee, needle))
                .toList();
    }

    public Employee getById(String id) {
        return employeeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Employee not found: " + id));
    }

    public Employee create(EmployeeRequest request) {
        String employeeId = normalizeEmployeeId(request.employeeId());
        String workEmail = normalizeEmail(request.workEmail());

        if (employeeRepository.existsByEmployeeIdIgnoreCase(employeeId)) {
            throw new DuplicateResourceException(
                    "An employee with ID " + employeeId + " already exists");
        }

        if (employeeRepository.existsByWorkEmailIgnoreCase(workEmail)) {
            throw new DuplicateResourceException(
                    "An employee with work email " + workEmail
                            + " already exists");
        }

        Employee employee = new Employee();
        apply(employee, request, employeeId, workEmail);
        employee.setCreatedAt(Instant.now());

        return employeeRepository.save(employee);
    }

    public Employee update(String id, EmployeeRequest request) {
        Employee existing = getById(id);

        String employeeId = normalizeEmployeeId(request.employeeId());
        String workEmail = normalizeEmail(request.workEmail());

        if (!equalsIgnoreCase(existing.getEmployeeId(), employeeId)
                && employeeRepository
                        .existsByEmployeeIdIgnoreCase(employeeId)) {
            throw new DuplicateResourceException(
                    "An employee with ID " + employeeId + " already exists");
        }

        if (!equalsIgnoreCase(existing.getWorkEmail(), workEmail)
                && employeeRepository
                        .existsByWorkEmailIgnoreCase(workEmail)) {
            throw new DuplicateResourceException(
                    "An employee with work email " + workEmail
                            + " already exists");
        }

        apply(existing, request, employeeId, workEmail);
        // createdAt is intentionally left untouched on update.

        return employeeRepository.save(existing);
    }

    public void delete(String id) {
        if (integrity != null) integrity.requireUnreferenced("employees",id);
        Employee existing = getById(id);
        employeeRepository.delete(existing);
    }

    // ----- helpers ------------------------------------------------------

    private void apply(
            Employee employee,
            EmployeeRequest request,
            String employeeId,
            String workEmail) {

        employee.setEmployeeId(employeeId);
        employee.setFullName(request.fullName().trim());
        employee.setWorkEmail(workEmail);
        employee.setPhone(blankToNull(request.phone()));
        employee.setDepartment(request.department().trim());
        employee.setDesignation(request.designation().trim());
        employee.setJoiningDate(request.joiningDate());
        employee.setSkills(normalizeSkills(request.skills()));
        employee.setReportingManager(blankToNull(request.reportingManager()));
        employee.setEmploymentStatus(normalizeStatus(request.employmentStatus()));
    }

    private boolean matches(Employee employee, String needle) {
        return contains(employee.getEmployeeId(), needle)
                || contains(employee.getFullName(), needle)
                || contains(employee.getWorkEmail(), needle)
                || contains(employee.getDepartment(), needle)
                || contains(employee.getDesignation(), needle)
                || contains(employee.getReportingManager(), needle)
                || (employee.getSkills() != null
                        && employee.getSkills().stream()
                                .anyMatch(skill -> contains(skill, needle)));
    }

    private boolean contains(String value, String needle) {
        return value != null
                && value.toLowerCase(Locale.ROOT).contains(needle);
    }

    private static String normalizeEmployeeId(String employeeId) {
        return employeeId.trim();
    }

    /** Emails are always compared and stored in lower-case. */
    public static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private static String normalizeStatus(String status) {
        if (status == null || status.isBlank()) {
            return DEFAULT_STATUS;
        }

        String normalized = status.trim().toUpperCase(Locale.ROOT);

        if (!ALLOWED_STATUSES.contains(normalized)) {
            throw new InvalidRequestException(
                    "Employment status must be one of "
                            + String.join(", ", ALLOWED_STATUSES));
        }

        return normalized;
    }

    private static List<String> normalizeSkills(List<String> skills) {
        if (skills == null) {
            return new ArrayList<>();
        }

        // Trim, drop blanks and de-duplicate while preserving order.
        return new ArrayList<>(skills.stream()
                .filter(skill -> skill != null && !skill.isBlank())
                .map(String::trim)
                .collect(LinkedHashSet::new, LinkedHashSet::add,
                        LinkedHashSet::addAll));
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private static boolean equalsIgnoreCase(String left, String right) {
        return left != null && right != null && left.equalsIgnoreCase(right);
    }
}
