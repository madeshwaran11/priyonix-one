package backend;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.MongoTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/** Mongo must run as a replica set: business mutations require transactions. */
@Configuration
@EnableTransactionManagement
public class WorkspaceTransactionConfiguration {
    @Bean(name="workspaceTransactionManager")
    public MongoTransactionManager workspaceTransactionManager(MongoDatabaseFactory factory) {
        return new MongoTransactionManager(factory);
    }
}
