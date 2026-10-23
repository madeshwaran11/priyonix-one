package backend;

import java.util.List;
import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface CampaignRepository extends MongoRepository<Campaign, String> {

    List<Campaign> findAllByOrderByCreatedAtDesc();

    Optional<Campaign> findByNameIgnoreCase(String name);

    long countByStatusIgnoreCase(String status);
}
