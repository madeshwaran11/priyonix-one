package backend;

/**
 * Lightweight employee reference embedded in project/task responses.
 *
 * The stable id is the MongoDB employee id; employeeId and fullName are
 * resolved from the referenced Employee record at read time.
 */
public record EmployeeRef(
        String id,
        String employeeId,
        String fullName,
        String workEmail) {

    public static EmployeeRef from(Employee employee) {
        if (employee == null) {
            return null;
        }
        return new EmployeeRef(
                employee.getId(),
                employee.getEmployeeId(),
                employee.getFullName(),
                employee.getWorkEmail());
    }
}
