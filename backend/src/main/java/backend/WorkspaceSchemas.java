package backend;

import java.util.*;
import org.springframework.stereotype.Component;
import static backend.WorkspaceSchema.*;

@Component
public class WorkspaceSchemas {
    private final Map<String,WorkspaceSchema> modules = new LinkedHashMap<>();
    public WorkspaceSchemas() {
        add("contacts","Contacts","CRM","People connected to client accounts.",
            fields(text("name","Name",true),email("email",true),text("phone","Phone",false),ref("clientId","Client","clients",false),text("jobTitle","Job title",false),notes()),
            "name,email,clientId", "ACTIVE", transitions(t("ACTIVE","ARCHIVED","Archive",false),t("ARCHIVED","ACTIVE","Restore",false)));
        add("opportunities","Opportunities","CRM","Track qualified deals through the sales pipeline.",
            fields(text("name","Opportunity",true),ref("clientId","Client","clients",true),ref("contactId","Contact","contacts",false),num("value","Expected value",true),date("closeDate","Expected close",true),ref("ownerId","Owner","employees",false),notes()),
            "name,clientId,value,closeDate", "QUALIFIED", transitions(t("QUALIFIED","PROPOSAL","Send proposal",false),t("PROPOSAL","NEGOTIATION","Negotiate",false),t("NEGOTIATION","WON","Mark won",false),t("QUALIFIED","LOST","Mark lost",false),t("PROPOSAL","LOST","Mark lost",false),t("NEGOTIATION","LOST","Mark lost",false)));
        add("quotations","Quotations","CRM","Single-line quotations with server-calculated tax and totals, in INR.",
            fields(text("number","Quote number",true),ref("clientId","Client","clients",true),ref("opportunityId","Opportunity","opportunities",false),text("description","Item / service",true),num("quantity","Quantity",true),num("unitPrice","Unit price (INR)",true),num("taxRate","Tax rate (%)",true),date("validUntil","Valid until",true),notes()),
            "number,clientId,description,total,validUntil", "DRAFT", transitions(t("DRAFT","SENT","Issue quote",false),t("SENT","ACCEPTED","Accept",true),t("SENT","REJECTED","Reject",false),t("DRAFT","CANCELLED","Cancel",false)));
        add("timesheets","Timesheets","Projects","Daily project time. Submitted entries require approval.",
            fields(ref("employeeId","Employee","employees",true),ref("projectId","Project","projects",true),date("date","Work date",true),num("hours","Hours",true),text("description","Work completed",true)),
            "employeeId,projectId,date,hours", "DRAFT", approvals());
        add("attendance","Attendance","People","One attendance entry per employee per day.",
            fields(ref("employeeId","Employee","employees",true),date("date","Date",true),select("presence","Attendance",true,"PRESENT","ABSENT","HALF_DAY","REMOTE"),num("hours","Hours worked",true),notes()),
            "employeeId,date,presence,hours", "RECORDED", transitions(t("RECORDED","VERIFIED","Verify",true)));
        add("leave","Leave requests","People","Calendar-day leave requests with overlap protection and approval history.",
            fields(ref("employeeId","Employee","employees",true),select("type","Leave type",true,"CASUAL","SICK","ANNUAL","UNPAID"),date("startDate","From",true),date("endDate","To",true),text("reason","Reason",true)),
            "employeeId,type,startDate,endDate,days", "DRAFT", approvals());
        add("vendors","Vendors","Procurement","Approved supplier directory.",
            fields(text("name","Vendor name",true),email("email",true),text("phone","Phone",false),text("taxId","Tax registration",false),text("address","Address",false),notes()),
            "name,email,phone,taxId", "ACTIVE", transitions(t("ACTIVE","INACTIVE","Deactivate",true),t("INACTIVE","ACTIVE","Activate",true)));
        add("purchase-requests","Purchase requests","Procurement","Request a single item or service before a purchase order can be raised.",
            fields(text("number","Request number",true),text("description","Item / service",true),num("quantity","Quantity",true),num("estimatedCost","Estimated total (INR)",false),date("neededDate","Needed by",true),text("reason","Business reason",false)),
            "number,description,quantity,estimatedCost,neededDate", "DRAFT", approvals());
        add("purchase-orders","Purchase orders","Procurement","One single-item order per approved request; amounts are calculated in INR.",
            fields(text("number","Order number",true),ref("requestId","Approved request","purchase-requests",true),ref("vendorId","Vendor","vendors",true),num("quantity","Quantity",true),num("unitPrice","Unit price (INR)",true),num("taxRate","Tax rate (%)",true),date("orderDate","Order date",true),date("expectedDate","Expected delivery",true),notes()),
            "number,requestId,vendorId,quantity,total,receivedQuantity", "DRAFT", transitions(t("DRAFT","ORDERED","Place order",true),t("DRAFT","CANCELLED","Cancel",true)));
        add("goods-receipts","Goods received","Procurement","Post deliveries against an open order; over-receipt is blocked.",
            fields(ref("orderId","Purchase order","purchase-orders",true),num("quantity","Received quantity",true),date("date","Received date",true),text("reference","Delivery reference",true),notes()),
            "reference,orderId,quantity,date", "DRAFT", transitions(t("DRAFT","RECEIVED","Post receipt",true),t("DRAFT","CANCELLED","Cancel",true)));
        add("assets","Assets","Assets","Asset register, employee allocation, returns and retirement.",
            fields(text("assetTag","Asset tag",true),text("name","Asset name",true),select("category","Category",true,"COMPUTER","EQUIPMENT","FURNITURE","VEHICLE","OTHER"),date("purchaseDate","Purchase date",true),num("value","Acquisition value (INR)",true),ref("employeeId","Allocate to","employees",false),text("location","Location",false),notes()),
            "assetTag,name,category,employeeId,location", "AVAILABLE", transitions(t("AVAILABLE","ALLOCATED","Allocate",true),t("ALLOCATED","AVAILABLE","Return",true),t("AVAILABLE","RETIRED","Retire",true)));
        add("maintenance","Maintenance","Assets","Plan and complete maintenance on an available asset.",
            fields(ref("assetId","Asset","assets",true),text("description","Work required",true),date("date","Scheduled date",true),num("cost","Cost (INR)",true),ref("vendorId","Vendor","vendors",false),notes()),
            "assetId,description,date,cost", "PLANNED", transitions(t("PLANNED","IN_PROGRESS","Start maintenance",true),t("IN_PROGRESS","COMPLETED","Complete",true),t("PLANNED","CANCELLED","Cancel",true),t("IN_PROGRESS","CANCELLED","Cancel",true)));
        add("courses","Courses","Training","Course catalog and training duration.",
            fields(text("code","Course code",true),text("name","Course name",true),num("hours","Duration (hours)",true),num("fee","Course fee (INR)",true),notes()),
            "code,name,hours,fee", "ACTIVE", transitions(t("ACTIVE","ARCHIVED","Archive",true)));
        add("batches","Batches","Training","Course cohorts, trainers and delivery dates.",
            fields(text("code","Batch code",true),text("name","Batch name",true),ref("courseId","Course","courses",true),ref("trainerId","Trainer","employees",true),date("startDate","Start date",true),date("endDate","End date",true),num("capacity","Student capacity",true)),
            "code,name,courseId,startDate,endDate,capacity", "PLANNED", transitions(t("PLANNED","ACTIVE","Start batch",true),t("ACTIVE","COMPLETED","Complete batch",true),t("PLANNED","CANCELLED","Cancel",true)));
        add("students","Students","Training","Students enrolled in a training batch.",
            fields(text("name","Student name",true),email("email",true),text("phone","Phone",false),ref("batchId","Batch","batches",true),date("joinedDate","Enrolment date",true),notes()),
            "name,email,batchId,joinedDate", "ENROLLED", transitions(t("ENROLLED","COMPLETED","Complete training",true),t("ENROLLED","WITHDRAWN","Withdraw",true)));
        add("training-attendance","Training attendance","Training","Daily attendance for a student in their enrolled batch.",
            fields(ref("studentId","Student","students",true),ref("batchId","Batch","batches",true),date("date","Class date",true),select("presence","Attendance",true,"PRESENT","ABSENT","LATE","EXCUSED"),notes()),
            "studentId,batchId,date,presence", "RECORDED", transitions(t("RECORDED","VERIFIED","Verify",true)));
        add("assessments","Assessments","Training","Assessment marks checked against a positive maximum score.",
            fields(ref("studentId","Student","students",true),ref("batchId","Batch","batches",true),text("name","Assessment",true),date("date","Assessment date",true),num("score","Score",true),num("maximum","Maximum score",true),notes()),
            "studentId,batchId,name,score,maximum,percentage", "DRAFT", transitions(t("DRAFT","PUBLISHED","Publish result",true)));
        add("placements","Placements","Training","Track student applications, offers and confirmed placements.",
            fields(ref("studentId","Student","students",true),text("company","Company",true),text("jobTitle","Job title",true),date("applicationDate","Application date",true),date("startDate","Joining date",false),num("salary","Annual salary (INR)",false),notes()),
            "studentId,company,jobTitle,salary,startDate", "APPLIED", transitions(t("APPLIED","INTERVIEW","Interview",true),t("INTERVIEW","OFFERED","Record offer",true),t("OFFERED","PLACED","Confirm placement",true),t("APPLIED","REJECTED","Reject",true),t("INTERVIEW","REJECTED","Reject",true),t("OFFERED","REJECTED","Decline",true)));
        add("budgets","Budgets","Finance","Department budget approvals for a defined period, in INR.",
            fields(text("name","Budget name",true),ref("departmentId","Department","departments",true),num("amount","Budget amount (INR)",true),date("startDate","Period start",true),date("endDate","Period end",true),notes()),
            "name,departmentId,amount,startDate,endDate", "DRAFT", transitions(t("DRAFT","APPROVED","Approve budget",true),t("APPROVED","CLOSED","Close budget",true)));
        add("departments","Departments","Administration","Organization departments and accountable managers.",
            fields(text("code","Department code",true),text("name","Department name",true),ref("managerId","Manager","employees",false),notes()),
            "code,name,managerId", "ACTIVE", transitions(t("ACTIVE","INACTIVE","Deactivate",true),t("INACTIVE","ACTIVE","Activate",true)));
    }
    public List<WorkspaceSchema> all() { return List.copyOf(modules.values()); }
    public WorkspaceSchema require(String key) {
        WorkspaceSchema schema = modules.get(key);
        if(schema == null) throw new ResourceNotFoundException("Unknown workspace module: " + key);
        return schema;
    }
    private void add(String key,String label,String group,String description,List<Field> fields,String columns,String initial,List<Transition> transitions) {
        var states = new LinkedHashSet<String>(); states.add(initial);
        for(var t:transitions) { states.add(t.from()); states.add(t.to()); }
        if(key.equals("purchase-orders")) { states.add("PARTIALLY_RECEIVED"); states.add("RECEIVED"); }
        if(key.equals("assets")) states.add("MAINTENANCE");
        modules.put(key,new WorkspaceSchema(key,label,group,description,fields,List.of(columns.split(",")),List.copyOf(states),initial,transitions));
    }
    private static Field field(String key,String label,String type,boolean required) { return new Field(key,label,type,required,List.of(),null); }
    private static Field text(String k,String l,boolean r) { return field(k,l,"text",r); }
    private static Field email(String k,boolean r) { return field(k,"Email","email",r); }
    private static Field date(String k,String l,boolean r) { return field(k,l,"date",r); }
    private static Field num(String k,String l,boolean r) { return field(k,l,"number",r); }
    private static Field notes() { return field("notes","Notes","textarea",false); }
    private static Field ref(String k,String l,String target,boolean r) { return new Field(k,l,"reference",r,List.of(),target); }
    private static Field select(String k,String l,boolean r,String...options) { return new Field(k,l,"select",r,List.of(options),null); }
    private static List<Field> fields(Field... f) { return List.of(f); }
    private static Transition t(String f,String to,String label,boolean admin) { return new Transition(f,to,label,admin); }
    private static List<Transition> transitions(Transition...t) { return List.of(t); }
    private static List<Transition> approvals() { return transitions(t("DRAFT","SUBMITTED","Submit for approval",false),t("SUBMITTED","APPROVED","Approve",true),t("SUBMITTED","REJECTED","Reject",true),t("REJECTED","DRAFT","Revise",false),t("DRAFT","CANCELLED","Cancel",false),t("SUBMITTED","CANCELLED","Withdraw",false)); }
}
