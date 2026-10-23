package backend;

import java.util.List;
import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface ProjectRepository extends MongoRepository<Project, String> {

    boolean existsByProjectCodeIgnoreCase(String projectCode);

    Optional<Project> findByProjectCodeIgnoreCase(String projectCode);

    List<Project> findAllByOrderByCreatedAtDesc();

    long countByStatusIgnoreCase(String status);
}
