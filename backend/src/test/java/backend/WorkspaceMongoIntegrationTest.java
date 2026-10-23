package backend;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import java.util.concurrent.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** Opt-in tests against a local Mongo replica set; every run gets a separate synthetic database. */
@EnabledIfEnvironmentVariable(named="WORKSPACE_MONGO_TEST_URI",matches=".+")
@SpringBootTest(properties={"JWT_SECRET=d29ya3NwYWNlLWludGVncmF0aW9uLXRlc3Qtc2VjcmV0LTMyaA==",
    "ADMIN_EMAIL=","ADMIN_PASSWORD=","ROLE_MIGRATION_ENABLED=false","platform.indexes.enabled=true"})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class WorkspaceMongoIntegrationTest {
    private static final String DATABASE="priyonix_workspace_test_"+UUID.randomUUID().toString().replace("-","");
    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry){
        registry.add("spring.mongodb.uri",()->System.getenv("WORKSPACE_MONGO_TEST_URI"));
        registry.add("spring.mongodb.database",()->DATABASE);
    }
    @Autowired WorkspaceService service;
    @Autowired MongoTemplate mongo;
    private final Authentication admin=new UsernamePasswordAuthenticationToken("test-admin@example.test",null,List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
    @AfterAll void removeOnlyThisSyntheticDatabase(){
        assertEquals(DATABASE,mongo.getDb().getName());assertTrue(DATABASE.startsWith("priyonix_workspace_test_"));mongo.getDb().drop();
    }
    @Test void recordsPersistUniqueKeysAndRejectStaleUpdates(){
        String email="person-"+UUID.randomUUID()+"@example.test";
        var contact=service.create("contacts",Map.of("name","Original","email",email),admin);
        assertEquals("Original",service.get("contacts",contact.getId(),admin).getValues().get("name"));
        var changed=service.update("contacts",contact.getId(),Map.of("name","Changed","email",email),contact.getVersion(),admin);
        assertThrows(ConflictException.class,()->service.update("contacts",contact.getId(),Map.of("name","Stale","email",email),contact.getVersion(),admin));
        assertThrows(RuntimeException.class,()->service.create("contacts",Map.of("name","Duplicate","email",email.toUpperCase(Locale.ROOT)),admin));
        assertEquals("Changed",service.get("contacts",changed.getId(),admin).getValues().get("name"));
    }
    @Test void concurrentReceiptsNeverOverReceiveAnOrder() throws Exception {
        String suffix=UUID.randomUUID().toString();String today=LocalDate.now().toString();
        var vendor=service.create("vendors",Map.of("name","Synthetic vendor","email","vendor-"+suffix+"@example.test"),admin);
        var request=service.create("purchase-requests",Map.of("number","PR-"+suffix,"description","Synthetic units","quantity",10,"neededDate",today),admin);
        request=service.transition("purchase-requests",request.getId(),"SUBMITTED",request.getVersion(),admin);
        request=service.transition("purchase-requests",request.getId(),"APPROVED",request.getVersion(),admin);
        var order=service.create("purchase-orders",Map.of("number","PO-"+suffix,"requestId",request.getId(),"vendorId",vendor.getId(),"quantity",10,"unitPrice",100,"taxRate",18,"orderDate",today,"expectedDate",today),admin);
        order=service.transition("purchase-orders",order.getId(),"ORDERED",order.getVersion(),admin);
        var first=service.create("goods-receipts",Map.of("orderId",order.getId(),"quantity",7,"date",today,"reference","DEL-A"),admin);
        var second=service.create("goods-receipts",Map.of("orderId",order.getId(),"quantity",7,"date",today,"reference","DEL-B"),admin);
        CountDownLatch start=new CountDownLatch(1);
        try(var workers=Executors.newFixedThreadPool(2)) {
            Future<Boolean> a=workers.submit(()->postAfter(start,first));Future<Boolean> b=workers.submit(()->postAfter(start,second));start.countDown();
            assertEquals(1,(a.get(30,TimeUnit.SECONDS)?1:0)+(b.get(30,TimeUnit.SECONDS)?1:0));
        }
        var persisted=service.get("purchase-orders",order.getId(),admin);
        assertEquals(new BigDecimal("7"),BusinessRules.number(persisted.getValues(),"receivedQuantity"));
        assertEquals("PARTIALLY_RECEIVED",persisted.getStatus());
    }
    private boolean postAfter(CountDownLatch start,WorkspaceRecord receipt) throws Exception {
        start.await(10,TimeUnit.SECONDS);
        try {service.transition("goods-receipts",receipt.getId(),"RECEIVED",receipt.getVersion(),admin);return true;}
        catch(RuntimeException expectedConflict){return false;}
    }
}
