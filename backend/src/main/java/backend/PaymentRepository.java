package backend;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface PaymentRepository extends MongoRepository<Payment, String> {

    List<Payment> findAllByOrderByCreatedAtDesc();

    List<Payment> findByInvoiceId(String invoiceId);

    List<Payment> findByInvoiceIdIn(Collection<String> invoiceIds);

    long countByInvoiceId(String invoiceId);

    boolean existsByReferenceNumberIgnoreCase(String referenceNumber);

    Optional<Payment> findByReferenceNumberIgnoreCase(String referenceNumber);

    List<Payment> findByPaymentDateBetween(LocalDate from, LocalDate to);

    void deleteByInvoiceId(String invoiceId);
}
