package backend;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface InvoiceRepository extends MongoRepository<Invoice, String> {

    boolean existsByInvoiceNumberIgnoreCase(String invoiceNumber);

    Optional<Invoice> findByInvoiceNumberIgnoreCase(String invoiceNumber);

    long countByInvoiceNumberStartingWith(String prefix);

    List<Invoice> findAllByOrderByCreatedAtDesc();

    List<Invoice> findByClientId(String clientId);

    long countByClientId(String clientId);

    long countByStatusIgnoreCase(String status);

    void deleteByClientId(String clientId);

    List<Invoice> findByIssueDateBetween(LocalDate from, LocalDate to);

    List<Invoice> findAllByIdIn(Collection<String> ids);
}
