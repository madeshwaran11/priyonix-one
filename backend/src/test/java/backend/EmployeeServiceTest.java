package backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EmployeeServiceTest {

    @Mock
    private EmployeeRepository employeeRepository;

    private EmployeeService employeeService;

    @BeforeEach
    void setUp() {
        employeeService = new EmployeeService(employeeRepository);
    }

    private EmployeeRequest request(String employeeId, String workEmail) {
        return new EmployeeRequest(
                employeeId,
                "  Asha Menon  ",
                workEmail,
                "  +91 98765 43210 ",
                "  Engineering ",
                " Senior Developer ",
                LocalDate.of(2023, 4, 1),
                List.of("Java", "Java", "  Spring Boot  ", "   "),
                "  Ravi Kumar ",
                null);
    }

    private Employee existingEmployee() {
        Employee employee = new Employee();
        employee.setId("mongo-1");
        employee.setEmployeeId("EMP-001");
        employee.setFullName("Asha Menon");
        employee.setWorkEmail("asha@acme.com");
        employee.setDepartment("Engineering");
        employee.setDesignation("Developer");
        employee.setEmploymentStatus("ACTIVE");
        employee.setCreatedAt(Instant.parse("2024-01-01T00:00:00Z"));
        return employee;
    }

    @Test
    void createNormalizesFieldsAndStampsTimestamp() {
        when(employeeRepository.existsByEmployeeIdIgnoreCase("EMP-042"))
                .thenReturn(false);
        when(employeeRepository.existsByWorkEmailIgnoreCase("asha@acme.com"))
                .thenReturn(false);
        when(employeeRepository.save(any(Employee.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        employeeService.create(request("  EMP-042 ", " Asha@ACME.com "));

        ArgumentCaptor<Employee> captor =
                ArgumentCaptor.forClass(Employee.class);
        verify(employeeRepository).save(captor.capture());

        Employee saved = captor.getValue();
        assertEquals("EMP-042", saved.getEmployeeId());
        assertEquals("Asha Menon", saved.getFullName());
        // Emails are always stored normalized (trimmed, lower-cased).
        assertEquals("asha@acme.com", saved.getWorkEmail());
        assertEquals("Engineering", saved.getDepartment());
        assertEquals("Senior Developer", saved.getDesignation());
        assertEquals("+91 98765 43210", saved.getPhone());
        assertEquals("Ravi Kumar", saved.getReportingManager());
        // Blank status defaults to ACTIVE.
        assertEquals("ACTIVE", saved.getEmploymentStatus());
        // Skills are trimmed, de-duplicated and blanks dropped.
        assertEquals(List.of("Java", "Spring Boot"), saved.getSkills());
        assertNotNull(saved.getCreatedAt());
    }

    @Test
    void createRejectsDuplicateEmployeeId() {
        when(employeeRepository.existsByEmployeeIdIgnoreCase("EMP-001"))
                .thenReturn(true);

        assertThrows(DuplicateResourceException.class,
                () -> employeeService.create(
                        request("EMP-001", "new@acme.com")));

        verify(employeeRepository, never()).save(any(Employee.class));
    }

    @Test
    void createRejectsDuplicateWorkEmailCaseInsensitively() {
        when(employeeRepository.existsByEmployeeIdIgnoreCase("EMP-002"))
                .thenReturn(false);
        when(employeeRepository.existsByWorkEmailIgnoreCase("asha@acme.com"))
                .thenReturn(true);

        assertThrows(DuplicateResourceException.class,
                () -> employeeService.create(
                        request("EMP-002", "Asha@Acme.Com")));

        verify(employeeRepository, never()).save(any(Employee.class));
    }

    @Test
    void createRejectsUnsupportedEmploymentStatus() {
        EmployeeRequest invalid = new EmployeeRequest(
                "EMP-003", "Ravi", "ravi@acme.com", null,
                "Engineering", "Tester", LocalDate.of(2022, 1, 1),
                List.of(), null, "banana");

        when(employeeRepository.existsByEmployeeIdIgnoreCase("EMP-003"))
                .thenReturn(false);
        when(employeeRepository.existsByWorkEmailIgnoreCase("ravi@acme.com"))
                .thenReturn(false);

        assertThrows(InvalidRequestException.class,
                () -> employeeService.create(invalid));

        verify(employeeRepository, never()).save(any(Employee.class));
    }

    @Test
    void listReturnsAllWhenSearchIsBlank() {
        when(employeeRepository.findAllByOrderByFullNameAsc())
                .thenReturn(List.of(existingEmployee()));

        assertEquals(1, employeeService.list(null).size());
        assertEquals(1, employeeService.list("   ").size());
    }

    @Test
    void listFiltersAcrossFields() {
        when(employeeRepository.findAllByOrderByFullNameAsc())
                .thenReturn(List.of(existingEmployee()));

        assertEquals(1, employeeService.list("asha").size());
        assertEquals(1, employeeService.list("engineering").size());
        assertEquals(1, employeeService.list("EMP-00").size());
        assertEquals(0, employeeService.list("marketing").size());
    }

    @Test
    void getByIdThrowsWhenMissing() {
        when(employeeRepository.findById("missing"))
                .thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> employeeService.getById("missing"));
    }

    @Test
    void updateKeepsCreatedAtAndAllowsUnchangedKeys() {
        Employee existing = existingEmployee();
        existing.setId("mongo-1");
        when(employeeRepository.findById("mongo-1"))
                .thenReturn(Optional.of(existing));
        when(employeeRepository.save(any(Employee.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // Same employee ID / email as stored: uniqueness checks are skipped.
        employeeService.update("mongo-1",
                request("EMP-001", "ASHA@acme.com"));

        ArgumentCaptor<Employee> captor =
                ArgumentCaptor.forClass(Employee.class);
        verify(employeeRepository).save(captor.capture());

        assertEquals(Instant.parse("2024-01-01T00:00:00Z"),
                captor.getValue().getCreatedAt());
        assertEquals("asha@acme.com", captor.getValue().getWorkEmail());

        verify(employeeRepository, never())
                .existsByEmployeeIdIgnoreCase(anyString());
    }

    @Test
    void updateRejectsDuplicateWhenKeysChange() {
        when(employeeRepository.findById("mongo-1"))
                .thenReturn(Optional.of(existingEmployee()));
        when(employeeRepository.existsByWorkEmailIgnoreCase("taken@acme.com"))
                .thenReturn(true);

        assertThrows(DuplicateResourceException.class,
                () -> employeeService.update("mongo-1",
                        request("EMP-001", "taken@acme.com")));

        verify(employeeRepository, never()).save(any(Employee.class));
    }

    @Test
    void deleteRemovesExistingEmployee() {
        Employee existing = existingEmployee();
        when(employeeRepository.findById("mongo-1"))
                .thenReturn(Optional.of(existing));

        employeeService.delete("mongo-1");

        verify(employeeRepository).delete(existing);
    }

    @Test
    void deleteThrowsWhenMissing() {
        when(employeeRepository.findById("missing"))
                .thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> employeeService.delete("missing"));

        verify(employeeRepository, never()).delete(any(Employee.class));
    }

    @Test
    void normalizeEmailTrimsAndLowerCases() {
        assertEquals("asha@acme.com",
                EmployeeService.normalizeEmail("  Asha@ACME.com "));
        assertTrue(EmployeeService.ALLOWED_STATUSES.contains("ACTIVE"));
    }
}
