package backend;

import java.time.LocalDate;
import org.bson.Document;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Component;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.UpdateOptions;

@Component
@ConditionalOnProperty(name="platform.indexes.enabled",havingValue="true",matchIfMissing=true)
public class LegacyIndexBootstrap implements ApplicationRunner {
    private final MongoTemplate mongo;
    public LegacyIndexBootstrap(MongoTemplate mongo){this.mongo=mongo;}
    public void run(ApplicationArguments args){
        for(String[] pair:new String[][]{{"employees","employeeId"},{"employees","workEmail"},{"projects","projectCode"},{"tasks","taskCode"},{"invoices","invoiceNumber"}}) {
            mongo.getCollection(pair[0]).createIndex(new Document(pair[1],1),new IndexOptions().unique(true).sparse(true).name(pair[1]));
        }
        mongo.getCollection("payments").createIndex(new Document("referenceNumber",1),new IndexOptions().unique(true)
            .partialFilterExpression(new Document("referenceNumber",new Document("$type","string"))).name("unique_payment_reference"));
        String prefix="INV-"+LocalDate.now().getYear()+"-";long maximum=0;
        for(Document invoice:mongo.getCollection("invoices").find(new Document("invoiceNumber",new Document("$regex","^"+prefix)))) {
            try {maximum=Math.max(maximum,Long.parseLong(invoice.getString("invoiceNumber").substring(prefix.length())));}catch(NumberFormatException ignored) { }
        }
        mongo.getCollection("sequences").updateOne(new Document("_id",prefix),new Document("$max",new Document("value",maximum)),new UpdateOptions().upsert(true));
    }
}
