package backend;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface FollowUpRepository extends MongoRepository<FollowUp, String> {

    List<FollowUp> findAllByOrderByCreatedAtDesc();

    List<FollowUp> findByLeadId(String leadId);

    List<FollowUp> findByClientId(String clientId);

    List<FollowUp> findByLeadIdIn(Collection<String> leadIds);

    List<FollowUp> findByClientIdIn(Collection<String> clientIds);

    long countByCompletedFalse();

    long countByCompletedFalseAndDueDateBefore(LocalDate dueDate);

    long countByAssignedToId(String assignedToId);

    long countByAssignedToIdAndCompletedFalse(String assignedToId);

    void deleteByLeadId(String leadId);

    void deleteByClientId(String clientId);
}
