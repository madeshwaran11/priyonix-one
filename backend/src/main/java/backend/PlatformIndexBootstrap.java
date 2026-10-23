package backend;

import org.bson.Document;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Component;
import com.mongodb.client.model.IndexOptions;

/** Idempotent database invariants. Index conflicts fail startup rather than silently weakening uniqueness. */
@Component
@ConditionalOnProperty(name = "platform.indexes.enabled", havingValue = "true", matchIfMissing = true)
public class PlatformIndexBootstrap implements ApplicationRunner {
    private final MongoTemplate mongo;
    public PlatformIndexBootstrap(MongoTemplate mongo) { this.mongo = mongo; }
    @Override public void run(ApplicationArguments arguments) {
        mongo.getCollection("users").createIndex(new Document("email", 1), new IndexOptions().unique(true).name("email"));
        mongo.getCollection("workspace_records").createIndex(new Document("businessKey", 1),
                new IndexOptions().unique(true).name("businessKey"));
        mongo.getCollection("workspace_records").createIndex(new Document("secondaryKey", 1),
                new IndexOptions().unique(true).sparse(true).name("secondaryKey"));
        mongo.getCollection("workspace_records").createIndex(new Document("module", 1).append("createdBy", 1).append("updatedAt", -1));
        mongo.getCollection("audit_events").createIndex(new Document("createdAt", -1));
        mongo.getCollection("platform_notifications").createIndex(new Document("recipient", 1).append("createdAt", -1));
        mongo.getCollection("platform_attachments").createIndex(new Document("module", 1).append("recordId", 1));
        mongo.getCollection("platform_locks").updateOne(new Document("_id", "account-administration"),
                new Document("$setOnInsert", new Document("revision", 0L)),
                new com.mongodb.client.model.UpdateOptions().upsert(true));
    }
}
