package backend;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import java.util.regex.Pattern;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static backend.BusinessRules.*;

@Service
public class WorkspaceService {
    private static final Set<String> PRIVATE=Set.of("timesheets","attendance","leave","purchase-requests");
    private static final Set<String> SHARED_WRITE=Set.of("contacts","opportunities","quotations");
    private static final Set<String> EMPLOYEE_RECORDS=Set.of("timesheets","attendance","leave");
    private final WorkspaceSchemas definitions;
    private final WorkspaceRecordRepository records;
    private final MongoTemplate mongo;
    private final EmployeeRepository employees;
    private final ClientRepository clients;
    private final ProjectRepository projects;
    private final LeadRepository leads;
    public WorkspaceService(WorkspaceSchemas definitions,WorkspaceRecordRepository records,MongoTemplate mongo,
            EmployeeRepository employees,ClientRepository clients,ProjectRepository projects,LeadRepository leads){
        this.definitions=definitions;this.records=records;this.mongo=mongo;this.employees=employees;this.clients=clients;this.projects=projects;this.leads=leads;
    }
    public List<WorkspaceSchema> schemas(){return definitions.all();}
    public WorkspaceRecord get(String module,String id,Authentication actor){
        definitions.require(module); WorkspaceRecord record=entity(module,id);
        if(!canRead(record,actor)) throw denied(); return record;
    }
    public boolean canRead(WorkspaceRecord record,Authentication actor){
        if(actor==null||!actor.isAuthenticated())return false;
        if(admin(actor)||SHARED_WRITE.contains(record.getModule()))return true;
        if(!PRIVATE.contains(record.getModule()))return false;
        if(EMPLOYEE_RECORDS.contains(record.getModule()))return employees.findByWorkEmailIgnoreCase(actor.getName())
            .map(e->Objects.equals(e.getId(),record.getValues().get("employeeId"))).orElse(false);
        return actor.getName().equals(record.getCreatedBy());
    }
    public PagedResult<WorkspaceRecord> list(String module,String search,String status,int page,int size,Authentication actor){
        var schema=definitions.require(module); requireActor(actor);
        int safePage=Math.max(1,page),safeSize=Math.max(1,Math.min(100,size));
        Criteria scope=Criteria.where("module").is(module);
        if(!admin(actor)) {
            if(!SHARED_WRITE.contains(module)&&!PRIVATE.contains(module))throw denied();
            if(EMPLOYEE_RECORDS.contains(module)) {
                String ownId=employees.findByWorkEmailIgnoreCase(actor.getName()).map(Employee::getId).orElse("__none__");
                scope.and("values.employeeId").is(ownId);
            } else if(PRIVATE.contains(module))scope.and("createdBy").is(actor.getName());
        }
        if(status!=null&&!status.isBlank()) { if(!schema.states().contains(status)) throw invalid("Unknown status"); scope.and("status").is(status); }
        if(search!=null&&!search.isBlank()) {
            if(search.length()>200) throw invalid("Search is too long");
            var terms=new ArrayList<Criteria>(); String regex=Pattern.quote(search.trim());
            for(var f:schema.fields()) if(!f.type().equals("number")) terms.add(Criteria.where("values."+f.key()).regex(regex,"i"));
            scope= new Criteria().andOperator(scope,new Criteria().orOperator(terms.toArray(Criteria[]::new)));
        }
        long total=mongo.count(new Query(scope),WorkspaceRecord.class);
        var query=new Query(scope).with(Sort.by(Sort.Direction.DESC,"updatedAt")).skip((long)(safePage-1)*safeSize).limit(safeSize);
        return new PagedResult<>(mongo.find(query,WorkspaceRecord.class),safePage,safeSize,total,(int)Math.min(Integer.MAX_VALUE,(total+safeSize-1)/safeSize));
    }
    @Transactional("workspaceTransactionManager")
    public WorkspaceRecord create(String module,Map<String,Object> input,Authentication actor){
        var schema=definitions.require(module); requireWrite(module,null,actor);
        var values=normalize(schema,input); validateReferences(schema,values,actor); validateBusiness(module,null,values,schema.initialState(),actor);
        var record=new WorkspaceRecord();record.setId(UUID.randomUUID().toString());record.setModule(module);record.setValues(values);record.setStatus(schema.initialState());
        record.setCreatedBy(actor.getName());record.setCreatedAt(Instant.now());record.setUpdatedAt(record.getCreatedAt());setKeys(record);
        return records.save(record);
    }
    @Transactional("workspaceTransactionManager")
    public WorkspaceRecord update(String module,String id,Map<String,Object> input,Long version,Authentication actor){
        var record=get(module,id,actor);requireWrite(module,record,actor);requireVersion(record,version);requireEditable(record);
        var values=normalize(definitions.require(module),input);validateReferences(definitions.require(module),values,actor);validateBusiness(module,id,values,record.getStatus(),actor);
        record.setValues(values);record.setUpdatedAt(Instant.now());setKeys(record);return records.save(record);
    }
    @Transactional("workspaceTransactionManager")
    public WorkspaceRecord transition(String module,String id,String target,Long version,Authentication actor){
        var record=get(module,id,actor);requireWrite(module,record,actor);requireVersion(record,version);
        var transition=definitions.require(module).transition(record.getStatus(),target);
        if(transition==null) throw invalid("This status change is not allowed");
        if(transition.adminOnly()&&!admin(actor)) throw denied();
        validateReferences(definitions.require(module),record.getValues(),actor);
        validateBusiness(module,id,record.getValues(),target,actor);
        applyTransition(record,target,actor);
        record.setStatus(target);record.setUpdatedAt(Instant.now());
        record.getValues().put("lastDecisionBy",actor.getName());record.getValues().put("lastDecisionAt",Instant.now().toString());
        return records.save(record);
    }
    @Transactional("workspaceTransactionManager")
    public void delete(String module,String id,Long version,Authentication actor){
        if(!admin(actor)) throw denied(); var record=get(module,id,actor);requireVersion(record,version);
        if(!Set.of("DRAFT","CANCELLED","REJECTED","PLANNED","ACTIVE","INACTIVE","ARCHIVED","AVAILABLE","APPLIED").contains(record.getStatus())) throw new ConflictException("Posted, approved or allocated records cannot be deleted");
        if(hasReferences(module,id)) throw new ConflictException("This record is used elsewhere. Archive it instead.");
        records.delete(record);
    }
    public boolean hasReferences(String module,String id){
        if(mongo.exists(new Query(Criteria.where("module").is(module).and("recordId").is(id)),"platform_attachments"))return true;
        for(var schema:definitions.all()) for(var field:schema.fields()) if(module.equals(field.reference())) {
            if(mongo.exists(new Query(Criteria.where("module").is(schema.key()).and("values."+field.key()).is(id)),WorkspaceRecord.class)) return true;
        }
        return false;
    }
    public List<Map<String,String>> lookups(String module,Authentication actor){
        requireActor(actor);
        return switch(module) {
            case "employees" -> employees.findAllByOrderByFullNameAsc().stream().map(e->lookup(e.getId(),e.getFullName())).toList();
            case "clients" -> clients.findAllByOrderByCreatedAtDesc().stream().map(c->lookup(c.getId(),c.getName())).toList();
            case "projects" -> projects.findAllByOrderByCreatedAtDesc().stream().map(p->lookup(p.getId(),p.getName())).toList();
            case "leads" -> leads.findAllByOrderByCreatedAtDesc().stream().map(l->lookup(l.getId(),l.getName())).toList();
            default -> { definitions.require(module);yield records.findByModule(module).stream().filter(r->canRead(r,actor)).map(r->lookup(r.getId(),label(r))).toList(); }
        };
    }
    public String label(WorkspaceRecord record){
        var v=record.getValues();for(String key:List.of("name","number","code","assetTag","reference","description","company")) if(v.get(key)!=null)return v.get(key).toString();
        return record.getModule()+" · "+record.getId().substring(0,Math.min(8,record.getId().length()));
    }
    private WorkspaceRecord entity(String module,String id){return records.findById(id).filter(r->module.equals(r.getModule())).orElseThrow(()->new ResourceNotFoundException("Record not found"));}
    private void validateReferences(WorkspaceSchema schema,Map<String,Object> values,Authentication actor){
        for(var field:schema.fields()) if(field.reference()!=null&&values.containsKey(field.key())) {
            String id=values.get(field.key()).toString();String target=field.reference();
            boolean exists=switch(target) {
                case "employees" -> employees.existsById(id); case "clients" -> clients.existsById(id);
                case "projects" -> projects.existsById(id);case "leads" -> leads.existsById(id);default -> {
                    var related=records.findById(id).filter(r->target.equals(r.getModule())).orElseThrow(()->invalid(field.label()+" does not exist"));
                    if(!canRead(related,actor))throw denied();
                    lockRecord(id); yield true;
                }
            };
            if(!exists) throw invalid(field.label()+" does not exist");
        }
    }
    private void validateBusiness(String module,String id,Map<String,Object> v,String target,Authentication actor){
        if(Set.of("attendance","timesheets","training-attendance","assessments","goods-receipts").contains(module)
                &&day(v,"date").isAfter(LocalDate.now()))throw invalid("Actual activity cannot be recorded on a future date");
        if(EMPLOYEE_RECORDS.contains(module)) {
            var employee=employees.findById(str(v,"employeeId")).orElseThrow(()->invalid("Employee does not exist"));
            if(!admin(actor)&&!actor.getName().equalsIgnoreCase(employee.getWorkEmail()))throw denied();
        }
        if(Set.of("CANCELLED","REJECTED").contains(target))return;
        if(module.equals("timesheets")&&!Set.of("REJECTED","CANCELLED").contains(target)) {
            guard("time:"+str(v,"employeeId")+":"+str(v,"date"));
            BigDecimal total=number(v,"hours");
            for(var other:matching("timesheets","employeeId",str(v,"employeeId"))) if(!Objects.equals(id,other.getId())&&!Set.of("REJECTED","CANCELLED").contains(other.getStatus())&&Objects.equals(v.get("date"),other.getValues().get("date"))) total=total.add(number(other.getValues(),"hours"));
            if(total.compareTo(new BigDecimal("24"))>0)throw invalid("Total timesheet hours cannot exceed 24 per employee per day");
        }
        if(module.equals("leave")&&!Set.of("REJECTED","CANCELLED").contains(target)) {
            guard("leave:"+str(v,"employeeId"));
            for(var other:matching("leave","employeeId",str(v,"employeeId"))) if(!Objects.equals(id,other.getId())&&!Set.of("REJECTED","CANCELLED").contains(other.getStatus())) {
                if(!day(v,"endDate").isBefore(day(other.getValues(),"startDate"))&&!day(v,"startDate").isAfter(day(other.getValues(),"endDate")))throw invalid("Leave dates overlap an existing request");
            }
        }
        if(module.equals("opportunities")&&v.containsKey("contactId")) {
            var contact=entity("contacts",str(v,"contactId"));Object client=contact.getValues().get("clientId");
            if(client!=null&&!client.equals(v.get("clientId")))throw invalid("Contact belongs to a different client");
        }
        if(module.equals("quotations")&&v.containsKey("opportunityId")) {
            if(!Objects.equals(entity("opportunities",str(v,"opportunityId")).getValues().get("clientId"),v.get("clientId")))throw invalid("Opportunity belongs to a different client");
        }
        if(module.equals("quotations")&&Set.of("SENT","ACCEPTED").contains(target)&&day(v,"validUntil").isBefore(LocalDate.now()))throw invalid("The quotation has expired");
        if(module.equals("purchase-orders")) {
            var request=entity("purchase-requests",str(v,"requestId"));
            if(!request.getStatus().equals("APPROVED"))throw invalid("Purchase order requires an approved purchase request");
            if(number(v,"quantity").compareTo(number(request.getValues(),"quantity"))>0)throw invalid("Order quantity exceeds the approved request");
            if(!entity("vendors",str(v,"vendorId")).getStatus().equals("ACTIVE"))throw invalid("Vendor must be active");
        }
        if(module.equals("goods-receipts")) {
            var order=entity("purchase-orders",str(v,"orderId"));
            if(!Set.of("ORDERED","PARTIALLY_RECEIVED").contains(order.getStatus()))throw invalid("Goods receipts require an open purchase order");
            if(day(v,"date").isBefore(day(order.getValues(),"orderDate")))throw invalid("Receipt date cannot precede the order");
            if(number(v,"quantity").compareTo(number(order.getValues(),"quantity").subtract(number(order.getValues(),"receivedQuantity")))>0)throw invalid("Received quantity exceeds the order balance");
        }
        if(module.equals("assets")&&target.equals("ALLOCATED")&&!v.containsKey("employeeId"))throw invalid("Choose an employee before allocating this asset");
        if(module.equals("maintenance")&&Set.of("PLANNED","IN_PROGRESS").contains(target)) {
            var asset=entity("assets",str(v,"assetId"));
            boolean current=id!=null&&entity("maintenance",id).getStatus().equals("IN_PROGRESS");
            if(!asset.getStatus().equals("AVAILABLE")&&!(current&&asset.getStatus().equals("MAINTENANCE")))throw invalid("Maintenance requires an available asset");
        }
        if(module.equals("batches")) {
            if(!entity("courses",str(v,"courseId")).getStatus().equals("ACTIVE"))throw invalid("Course must be active");
            if(id!=null&&matching("students","batchId",id).stream().filter(r->r.getStatus().equals("ENROLLED")).count()>number(v,"capacity").longValue())throw invalid("Capacity is below the current enrolment");
        }
        if(module.equals("students")) {
            var batch=entity("batches",str(v,"batchId"));
            if(day(v,"joinedDate").isAfter(day(batch.getValues(),"endDate")))throw invalid("Enrolment date cannot be after the batch ends");
            if(id!=null&&!Objects.equals(entity("students",id).getValues().get("batchId"),v.get("batchId"))&&hasReferences("students",id))
                throw new ConflictException("This student already has training or placement history. Keep their batch reference.");
            if(id!=null&&!Objects.equals(entity("students",id).getValues().get("batchId"),v.get("batchId"))&&hasReferences("students",id))
                throw new ConflictException("This student already has training or placement history. Keep their batch reference.");
            if(!Set.of("PLANNED","ACTIVE").contains(batch.getStatus())&&target.equals("ENROLLED"))throw invalid("Batch is closed to enrolment");
            long count=matching("students","batchId",str(v,"batchId")).stream().filter(r->!Objects.equals(r.getId(),id)&&r.getStatus().equals("ENROLLED")).count();
            if(target.equals("ENROLLED")&&count>=number(batch.getValues(),"capacity").longValue())throw invalid("Batch capacity has been reached");
        }
        if(module.equals("training-attendance")||module.equals("assessments")) {
            var student=entity("students",str(v,"studentId"));
            if(!Objects.equals(student.getValues().get("batchId"),v.get("batchId")))throw invalid("Student is not enrolled in this batch");
            var batch=entity("batches",str(v,"batchId"));
            if(day(v,"date").isBefore(day(batch.getValues(),"startDate"))||day(v,"date").isAfter(day(batch.getValues(),"endDate")))throw invalid("Date must be within the batch dates");
        }
        if(module.equals("placements")&&target.equals("PLACED")&&(!v.containsKey("startDate")||number(v,"salary").signum()<=0))throw invalid("A confirmed placement needs a joining date and positive salary");
        if(module.equals("placements")&&v.containsKey("startDate")&&day(v,"startDate").isBefore(day(v,"applicationDate")))throw invalid("Joining date cannot precede the application");
        if(module.equals("budgets")&&number(v,"amount").signum()<=0)throw invalid("Budget amount must be greater than zero");
    }
    private void applyTransition(WorkspaceRecord record,String target,Authentication actor){
        var v=record.getValues();
        if(record.getModule().equals("goods-receipts")&&target.equals("RECEIVED")) {
            var order=entity("purchase-orders",str(v,"orderId"));
            BigDecimal received=number(order.getValues(),"receivedQuantity").add(number(v,"quantity"));
            if(received.compareTo(number(order.getValues(),"quantity"))>0)throw invalid("Received quantity exceeds the order balance");
            order.getValues().put("receivedQuantity",received);order.setStatus(received.compareTo(number(order.getValues(),"quantity"))==0?"RECEIVED":"PARTIALLY_RECEIVED");order.setUpdatedAt(Instant.now());records.save(order);
        }
        if(record.getModule().equals("assets")&&Set.of("AVAILABLE","RETIRED").contains(target))v.remove("employeeId");
        if(record.getModule().equals("maintenance")&&(target.equals("IN_PROGRESS")||record.getStatus().equals("IN_PROGRESS"))) {
            var asset=entity("assets",str(v,"assetId"));asset.setStatus(target.equals("IN_PROGRESS")?"MAINTENANCE":"AVAILABLE");asset.setUpdatedAt(Instant.now());records.save(asset);
        }
    }
    private void requireEditable(WorkspaceRecord record){
        String m=record.getModule(),s=record.getStatus();
        boolean editable=Set.of("DRAFT","REJECTED","PLANNED","RECORDED").contains(s)
            ||Set.of("contacts","vendors","courses","departments").contains(m)
            ||m.equals("opportunities")&&!Set.of("WON","LOST").contains(s)
            ||m.equals("assets")&&s.equals("AVAILABLE")
            ||m.equals("students")&&s.equals("ENROLLED")
            ||m.equals("placements")&&!Set.of("PLACED","REJECTED").contains(s);
        if(!editable)throw new ConflictException("This record is locked by its workflow status");
    }
    private void requireWrite(String module,WorkspaceRecord record,Authentication actor){
        requireActor(actor);if(admin(actor))return;
        if(SHARED_WRITE.contains(module))return;
        if(PRIVATE.contains(module)&&(record==null||canRead(record,actor)))return;
        throw denied();
    }
    private void requireVersion(WorkspaceRecord record,Long version){if(version==null||!Objects.equals(record.getVersion(),version))throw new ConflictException("This record changed. Refresh it before trying again.");}
    private void setKeys(WorkspaceRecord r){
        Map<String,Object> v=r.getValues();String m=r.getModule();
        String key=switch(m){
            case "contacts","vendors","students" -> str(v,"email");
            case "courses","batches","departments" -> str(v,"code");
            case "assets" -> str(v,"assetTag");case "quotations","purchase-requests" -> str(v,"number");
            case "purchase-orders" -> str(v,"requestId");
            case "attendance" -> str(v,"employeeId")+"|"+str(v,"date");
            case "timesheets" -> str(v,"employeeId")+"|"+str(v,"projectId")+"|"+str(v,"date");
            case "training-attendance" -> str(v,"studentId")+"|"+str(v,"batchId")+"|"+str(v,"date");
            case "assessments" -> str(v,"studentId")+"|"+str(v,"batchId")+"|"+str(v,"name")+"|"+str(v,"date");
            case "goods-receipts" -> str(v,"orderId")+"|"+str(v,"reference");
            default -> r.getId();
        };
        r.businessKey(m+"|"+key.toLowerCase(Locale.ROOT));
        if(m.equals("purchase-orders"))r.secondaryKey(m+"|"+str(v,"number").toLowerCase(Locale.ROOT));
    }
    private List<WorkspaceRecord> matching(String module,String field,String value){return mongo.find(new Query(Criteria.where("module").is(module).and("values."+field).is(value)),WorkspaceRecord.class);}
    private void guard(String key){mongo.upsert(new Query(Criteria.where("_id").is(key)),new Update().inc("revision",1),BusinessGuard.class);}
    private void lockRecord(String id){mongo.updateFirst(new Query(Criteria.where("_id").is(id)),new Update().inc("guardRevision",1).inc("version",0),WorkspaceRecord.class);}
    private static boolean admin(Authentication actor){return actor!=null&&actor.getAuthorities().stream().anyMatch(a->a.getAuthority().equals("ROLE_ADMIN"));}
    private static void requireActor(Authentication actor){if(actor==null||!actor.isAuthenticated())throw denied();}
    private static AccessDeniedException denied(){return new AccessDeniedException("You do not have permission for this operation");}
    private static InvalidRequestException invalid(String text){return new InvalidRequestException(text);}
    private static String str(Map<String,Object> v,String key){return Objects.toString(v.get(key),"");}
    private static Map<String,String> lookup(String id,String label){return Map.of("id",id,"label",label==null?id:label);}
}
