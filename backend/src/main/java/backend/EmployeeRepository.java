package backend;

import java.util.List;
import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface EmployeeRepository extends MongoRepository<Employee, String> {

    Optional<Employee> findByEmployeeIdIgnoreCase(String employeeId);

    Optional<Employee> findByWorkEmailIgnoreCase(String workEmail);

    boolean existsByEmployeeIdIgnoreCase(String employeeId);

    boolean existsByWorkEmailIgnoreCase(String workEmail);

    List<Employee> findAllByOrderByFullNameAsc();
}
