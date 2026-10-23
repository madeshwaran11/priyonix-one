package backend;

import java.time.LocalDate;
import java.util.List;
import org.bson.Document;
import org.springframework.stereotype.Service;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

/** Shared database locks and foreign-reference protection for the original modules. */
@Service
public class LegacyIntegrityService {
    private final MongoTemplate mongo;
    private final WorkspaceService workspace;
    public LegacyIntegrityService(MongoTemplate mongo, WorkspaceService workspace) { this.mongo=mongo;this.workspace=workspace; }
    public void touchInvoice(String id) {
        mongo.updateFirst(Query.query(Criteria.where("_id").is(id)),new Update().inc("mutationVersion",1),"invoices");
    }
    public String nextInvoiceNumber() {
        String prefix="INV-"+LocalDate.now().getYear()+"-";
        // Atomic counter; startup seeds it from existing invoice numbers before serving traffic.
        Document next=mongo.findAndModify(Query.query(Criteria.where("_id").is(prefix)),new Update().inc("value",1),
            FindAndModifyOptions.options().upsert(true).returnNew(true),Document.class,"sequences");
        return prefix+String.format("%06d",((Number)next.get("value")).longValue());
    }
    public void requireUnreferenced(String module,String id) {
        if(workspace.hasReferences(module,id)) throw new ConflictException("This record is referenced by another module or attachment.");
        if(module.equals("clients") && exists("invoices","clientId",id)) conflict();
        if(module.equals("projects") && exists("invoices","projectId",id)) conflict();
        if(module.equals("employees")) {
            for(String collection:List.of("projects","tasks","leads","clients","followups","follow_ups")) {
                for(String field:List.of("managerId","teamMemberIds","assignedToId","assigneeId","ownerId","assignedTo","employeeId")) {
                    if(exists(collection,field,id)) conflict();
                }
            }
        }
    }
    private boolean exists(String collection,String field,String id) {return mongo.exists(Query.query(Criteria.where(field).is(id)),collection);}
    private static void conflict(){throw new ConflictException("This record is in use. Update its related records before deleting.");}
}
