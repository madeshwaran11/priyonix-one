package backend;

import java.util.List;
import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface ClientRepository extends MongoRepository<Client, String> {

    boolean existsByEmailIgnoreCase(String email);

    Optional<Client> findByEmailIgnoreCase(String email);

    List<Client> findAllByOrderByCreatedAtDesc();

    long countByStatusIgnoreCase(String status);
}
