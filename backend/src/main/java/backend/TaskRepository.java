package backend;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface TaskRepository extends MongoRepository<Task, String> {

    boolean existsByTaskCodeIgnoreCase(String taskCode);

    List<Task> findAllByOrderByCreatedAtDesc();

    List<Task> findByProjectId(String projectId);

    long countByProjectId(String projectId);

    long countByStatusIgnoreCase(String status);

    long countByDueDateBeforeAndStatusNot(LocalDate dueDate, String status);

    long countByAssigneeId(String assigneeId);

    long countByAssigneeIdAndStatusNot(String assigneeId, String status);

    void deleteByProjectId(String projectId);
}
