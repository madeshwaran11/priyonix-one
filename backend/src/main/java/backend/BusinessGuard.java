package backend;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/** One transactional conflict point for invariants spanning several records. */
@Document("business_guards")
public class BusinessGuard {
    @Id private String id;
    private long revision;
}
