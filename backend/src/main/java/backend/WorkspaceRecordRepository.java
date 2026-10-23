package backend;
import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;
public interface WorkspaceRecordRepository extends MongoRepository<WorkspaceRecord,String> {
    List<WorkspaceRecord> findByModule(String module);
}
