package backend;

import java.util.Collection;
import java.util.List;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface LeadRepository extends MongoRepository<Lead, String> {

    List<Lead> findAllByOrderByCreatedAtDesc();

    List<Lead> findByCampaignId(String campaignId);

    List<Lead> findByCampaignIdIn(Collection<String> campaignIds);

    long countByStatusIgnoreCase(String status);

    long countByCampaignIdNotNull();

    long countByCampaignIdNotNullAndConvertedClientIdNotNull();
}
