package backend;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.access.AccessDeniedException;

/** Focused business-rule tests; these mocks do not claim real Mongo persistence/concurrency coverage. */
class WorkspaceServiceTest {
    WorkspaceRecordRepository records=mock(WorkspaceRecordRepository.class);
    MongoTemplate mongo=mock(MongoTemplate.class);
    EmployeeRepository employees=mock(EmployeeRepository.class);
    ClientRepository clients=mock(ClientRepository.class);
    ProjectRepository projects=mock(ProjectRepository.class);
    LeadRepository leads=mock(LeadRepository.class);
    WorkspaceService service;
    Authentication admin=actor("admin@example.test","ADMIN"),employee=actor("employee@example.test","EMPLOYEE");
    @BeforeEach void setup(){
        service=new WorkspaceService(new WorkspaceSchemas(),records,mongo,employees,clients,projects,leads);
        when(mongo.find(any(Query.class),eq(WorkspaceRecord.class))).thenReturn(List.of());
        when(records.save(any(WorkspaceRecord.class))).thenAnswer(invocation->{var r=invocation.<WorkspaceRecord>getArgument(0);r.setVersion(r.getVersion()==null?0:r.getVersion()+1);return r;});
    }
    static Authentication actor(String email,String role){return new UsernamePasswordAuthenticationToken(email,null,List.of(new SimpleGrantedAuthority("ROLE_"+role)));}
    WorkspaceRecord record(String module,String id,String status,Map<String,Object> values){
        var r=new WorkspaceRecord();r.setId(id);r.setModule(module);r.setStatus(status);r.setValues(values);r.setVersion(0L);r.setCreatedBy(employee.getName());r.setCreatedAt(Instant.now());r.setUpdatedAt(Instant.now());
        when(records.findById(id)).thenReturn(Optional.of(r));return r;
    }
    void identity(String id,String email){var e=new Employee();e.setId(id);e.setWorkEmail(email);when(employees.existsById(id)).thenReturn(true);when(employees.findById(id)).thenReturn(Optional.of(e));}

    @Test void employeeCannotCreateFinanceOrProcurementOrders(){
        assertThrows(AccessDeniedException.class,()->service.create("budgets",Map.of(),employee));
        assertThrows(AccessDeniedException.class,()->service.create("purchase-orders",Map.of(),employee));
    }
    @Test void employeeCannotReadAnotherPersonsLeave(){
        var r=record("leave","leave1","DRAFT",Map.of());r.setCreatedBy("other@example.test");
        assertThrows(AccessDeniedException.class,()->service.get("leave","leave1",employee));
        assertSame(r,service.get("leave","leave1",admin));
    }
    @Test void employeeSeesOwnAttendanceCreatedByAnAdministrator(){
        var e=new Employee();e.setId("e1");when(employees.findByWorkEmailIgnoreCase(employee.getName())).thenReturn(Optional.of(e));
        var r=record("attendance","attendance1","RECORDED",Map.of("employeeId","e1"));r.setCreatedBy(admin.getName());
        assertSame(r,service.get("attendance","attendance1",employee));
    }
    @Test void employeeCannotReadStudentOrBudgetRecords(){
        var student=record("students","s1","ENROLLED",Map.of("email","student@example.test"));
        var budget=record("budgets","b1","APPROVED",Map.of("amount",100000));
        assertFalse(service.canRead(student,employee));assertFalse(service.canRead(budget,employee));
    }
    @Test void employeeOwnershipUsesEmployeeIdentityRatherThanCreatorOnly(){
        var e=new Employee();e.setId("e1");when(employees.findByWorkEmailIgnoreCase(employee.getName())).thenReturn(Optional.of(e));
        var r=record("leave","l1","DRAFT",Map.of("employeeId","another-person"));
        assertFalse(service.canRead(r,employee));
    }
    @Test void employeeCannotSpoofEmployeeOnAttendance(){
        identity("other","other@example.test");
        assertThrows(AccessDeniedException.class,()->service.create("attendance",Map.of("employeeId","other","date","2026-10-22","presence","PRESENT","hours",8),employee));
    }
    @Test void staleVersionCannotOverwriteChanges(){
        record("contacts","c1","ACTIVE",Map.of("name","Asha","email","asha@example.test"));
        assertThrows(ConflictException.class,()->service.update("contacts","c1",Map.of("name","Asha","email","asha@example.test"),5L,employee));
        verify(records,never()).save(any());
    }
    @Test void employeeCannotApproveOwnRequest(){
        record("purchase-requests","pr1","SUBMITTED",Map.of("number","PR1"));
        assertThrows(AccessDeniedException.class,()->service.transition("purchase-requests","pr1","APPROVED",0L,employee));
    }
    @Test void approvalCannotBeSkipped(){
        record("purchase-requests","pr1","DRAFT",Map.of("number","PR1"));
        assertThrows(InvalidRequestException.class,()->service.transition("purchase-requests","pr1","APPROVED",0L,admin));
    }
    @Test void submittedRequestCannotBeEdited(){
        record("purchase-requests","pr1","SUBMITTED",Map.of("number","PR1"));
        assertThrows(ConflictException.class,()->service.update("purchase-requests","pr1",Map.of(),0L,employee));
    }
    @Test void timeEntriesAcrossProjectsCannotExceedDailyLimit(){
        identity("e1",employee.getName());when(projects.existsById("p2")).thenReturn(true);
        var existing=record("timesheets","t1","APPROVED",Map.of("employeeId","e1","date","2026-10-21","hours",20));
        when(mongo.find(any(Query.class),eq(WorkspaceRecord.class))).thenReturn(List.of(existing));
        assertThrows(InvalidRequestException.class,()->service.create("timesheets",Map.of("employeeId","e1","projectId","p2","date","2026-10-21","hours",5,"description","Support"),employee));
    }
    @Test void overlappingLeaveDatesAreRejected(){
        identity("e1",employee.getName());
        var existing=record("leave","l1","APPROVED",Map.of("employeeId","e1","startDate","2026-11-01","endDate","2026-11-04"));
        when(mongo.find(any(Query.class),eq(WorkspaceRecord.class))).thenReturn(List.of(existing));
        assertThrows(InvalidRequestException.class,()->service.create("leave",Map.of("employeeId","e1","type","ANNUAL","startDate","2026-11-04","endDate","2026-11-05","reason","Trip"),employee));
    }
    @Test void orderRequiresApprovedPurchaseRequest(){
        record("purchase-requests","pr1","SUBMITTED",Map.of("quantity",5));record("vendors","v1","ACTIVE",Map.of());
        assertThrows(InvalidRequestException.class,()->service.create("purchase-orders",Map.of("number","PO1","requestId","pr1","vendorId","v1","quantity",3,"unitPrice",100,"taxRate",18,"orderDate","2026-10-22","expectedDate","2026-11-01"),admin));
    }
    @Test void postingReceiptUpdatesOrderBalanceAndCompletion(){
        var order=record("purchase-orders","po1","PARTIALLY_RECEIVED",Map.of("quantity",10,"receivedQuantity",6,"orderDate","2026-10-20"));
        var receipt=record("goods-receipts","gr1","DRAFT",Map.of("orderId","po1","quantity",4,"date","2026-10-22","reference","DEL-2"));
        service.transition("goods-receipts","gr1","RECEIVED",0L,admin);
        assertEquals("RECEIVED",order.getStatus());assertEquals(new BigDecimal("10"),order.getValues().get("receivedQuantity"));assertEquals("RECEIVED",receipt.getStatus());
    }
    @Test void receiptCannotExceedUnreceivedQuantity(){
        record("purchase-orders","po1","PARTIALLY_RECEIVED",Map.of("quantity",10,"receivedQuantity",6,"orderDate","2026-10-20"));
        record("goods-receipts","gr1","DRAFT",Map.of("orderId","po1","quantity",5,"date","2026-10-22","reference","DEL-2"));
        assertThrows(InvalidRequestException.class,()->service.transition("goods-receipts","gr1","RECEIVED",0L,admin));
    }
    @Test void allocationRequiresAnEmployeeAndReturnClearsAllocation(){
        var asset=record("assets","a1","AVAILABLE",Map.of("name","Laptop"));
        assertThrows(InvalidRequestException.class,()->service.transition("assets","a1","ALLOCATED",0L,admin));
        identity("e1",employee.getName());asset.getValues().put("employeeId","e1");
        service.transition("assets","a1","ALLOCATED",0L,admin);assertEquals("ALLOCATED",asset.getStatus());
        service.transition("assets","a1","AVAILABLE",1L,admin);assertFalse(asset.getValues().containsKey("employeeId"));
    }
    @Test void trainingAttendanceRejectsStudentFromAnotherBatch(){
        record("students","s1","ENROLLED",Map.of("batchId","b2"));record("batches","b1","ACTIVE",Map.of("startDate","2026-10-01","endDate","2026-12-31"));
        assertThrows(InvalidRequestException.class,()->service.create("training-attendance",Map.of("studentId","s1","batchId","b1","date","2026-10-22","presence","PRESENT"),admin));
    }
    @Test void referencedCatalogRecordCannotBeDeleted(){
        record("departments","d1","ACTIVE",Map.of("name","Sales"));when(mongo.exists(any(Query.class),eq(WorkspaceRecord.class))).thenReturn(true);
        assertThrows(ConflictException.class,()->service.delete("departments","d1",0L,admin));
    }
    @Test void approvedRecordsCannotBeDeleted(){
        record("budgets","b1","APPROVED",Map.of());assertThrows(ConflictException.class,()->service.delete("budgets","b1",0L,admin));
    }
    @Test void attachmentsPreventDeletingTheirOwningRecord(){
        record("contacts","c1","ACTIVE",Map.of("name","Asha"));
        when(mongo.exists(any(Query.class),eq("platform_attachments"))).thenReturn(true);
        assertThrows(ConflictException.class,()->service.delete("contacts","c1",0L,admin));
    }
    @Test void missingReferenceIsAValidationError(){
        assertThrows(InvalidRequestException.class,()->service.create("budgets",Map.of("name","Budget","departmentId","missing","amount",100,"startDate","2026-10-01","endDate","2026-12-31"),admin));
    }
    @Test void fullBatchRejectsAdditionalEnrolment(){
        record("batches","b1","ACTIVE",Map.of("capacity",1,"endDate","2026-12-31"));
        var enrolled=record("students","s1","ENROLLED",Map.of("batchId","b1"));
        when(mongo.find(any(Query.class),eq(WorkspaceRecord.class))).thenReturn(List.of(enrolled));
        assertThrows(InvalidRequestException.class,()->service.create("students",Map.of("name","New learner","email","new@example.test","batchId","b1","joinedDate","2026-10-22"),admin));
    }
    @Test void maintenanceMarksAssetUnavailableUntilCompletion(){
        var asset=record("assets","a1","AVAILABLE",Map.of("name","Laptop"));
        var maintenance=record("maintenance","m1","PLANNED",Map.of("assetId","a1","description","Inspect","date","2026-10-22","cost",100));
        service.transition("maintenance","m1","IN_PROGRESS",0L,admin);assertEquals("MAINTENANCE",asset.getStatus());
        service.transition("maintenance","m1","COMPLETED",maintenance.getVersion(),admin);assertEquals("AVAILABLE",asset.getStatus());
    }
    @Test void unusedDraftReceiptCanBeCancelledAfterTheOrderCloses(){
        record("purchase-orders","po1","RECEIVED",Map.of("quantity",10,"receivedQuantity",10,"orderDate","2026-10-20"));
        var receipt=record("goods-receipts","gr1","DRAFT",Map.of("orderId","po1","quantity",5,"date","2026-10-22","reference","Unused"));
        service.transition("goods-receipts","gr1","CANCELLED",0L,admin);assertEquals("CANCELLED",receipt.getStatus());
    }
}
