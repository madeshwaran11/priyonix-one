package backend;

import static org.junit.jupiter.api.Assertions.*;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class WorkspaceBusinessRulesTest {
    private final WorkspaceSchemas schemas = new WorkspaceSchemas();

    @Test void quotationsCalculateRoundedTaxAndTotalOnServer() {
        var values = BusinessRules.normalize(schemas.require("quotations"), Map.of(
            "number", "Q-101", "clientId", "client1", "description", "Design", "quantity", 3,
            "unitPrice", "99.995", "taxRate", 18, "validUntil", "2026-12-31"));
        assertEquals(new BigDecimal("299.99"), values.get("subtotal"));
        assertEquals(new BigDecimal("54.00"), values.get("tax"));
        assertEquals(new BigDecimal("353.99"), values.get("total"));
    }

    @Test void requestCannotSupplyCalculatedTotalOrArbitraryFields() {
        assertThrows(InvalidRequestException.class, () -> BusinessRules.normalize(
            schemas.require("contacts"), Map.of("name","Asha", "email","asha@example.test", "role","ADMIN")));
        assertThrows(InvalidRequestException.class, () -> BusinessRules.normalize(
            schemas.require("quotations"), Map.of("total",0)));
    }

    @Test void malformedReferencesAndNestedObjectsAreRejected() {
        assertThrows(InvalidRequestException.class, () -> BusinessRules.normalize(
            schemas.require("contacts"), Map.of("name",Map.of("$ne",""),"email","asha@example.test")));
    }

    @Test void leaveDatesAreInclusiveAndCannotBeReversed() {
        var request = new LinkedHashMap<String,Object>(Map.of("employeeId","emp1","type","CASUAL",
            "startDate","2026-11-01","endDate","2026-11-03","reason","Family event"));
        var values = BusinessRules.normalize(schemas.require("leave"), request);
        assertEquals(3L, values.get("days"));
        request.put("endDate","2026-10-31");
        assertThrows(InvalidRequestException.class, () -> BusinessRules.normalize(schemas.require("leave"), request));
    }

    @Test void timeEntryCannotExceedTwentyFourHours() {
        assertThrows(InvalidRequestException.class, () -> BusinessRules.normalize(schemas.require("timesheets"),
            Map.of("employeeId","emp1","projectId","p1","date","2026-10-21","hours",25,"description","Delivery")));
    }

    @Test void assessmentScoreCannotExceedMaximum() {
        assertThrows(InvalidRequestException.class, () -> BusinessRules.normalize(schemas.require("assessments"),
            Map.of("studentId","s1","batchId","b1","name","Java", "date","2026-10-21","score",101,"maximum",100)));
    }

    @Test void numberInputsRejectNaNAndNegativeQuantities() {
        assertThrows(InvalidRequestException.class, () -> BusinessRules.normalize(schemas.require("purchase-requests"),
            Map.of("number","PR-1","description","Laptops","quantity",-1,"neededDate","2026-11-30")));
        assertThrows(InvalidRequestException.class, () -> BusinessRules.normalize(schemas.require("budgets"),
            Map.of("name","Budget","departmentId","d1","amount","NaN","startDate","2026-01-01","endDate","2026-12-31")));
    }

    @Test void statusesCannotSkipApproval() {
        assertNull(schemas.require("purchase-requests").transition("DRAFT", "APPROVED"));
        assertNotNull(schemas.require("purchase-requests").transition("SUBMITTED", "APPROVED"));
        assertTrue(schemas.require("purchase-requests").transition("SUBMITTED", "APPROVED").adminOnly());
    }
}
