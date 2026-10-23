package backend;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface ExpenseRepository extends MongoRepository<Expense, String> {

    List<Expense> findAllByOrderByCreatedAtDesc();

    long countByStatusIgnoreCase(String status);

    long countByStatusIn(Collection<String> statuses);

    List<Expense> findByExpenseDateBetween(LocalDate from, LocalDate to);
}
