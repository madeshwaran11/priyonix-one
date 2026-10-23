package backend;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * End-to-end tests for the Finance reporting API: JWT authentication, the
 * computed summary and the CSV export.
 */
@SpringBootTest(properties = {
        "spring.mongodb.uri=mongodb://127.0.0.1:27017/priyonix-test",
        "JWT_SECRET=dGVzdC1zZWNyZXQtZm9yLXVuaXQtYnVpbGRzLW9ubHktMzItYnl0ZXM=",
        "ADMIN_EMAIL=",
        "ADMIN_PASSWORD="
})
@AutoConfigureMockMvc
class FinanceApiIntegrationTests extends PlatformMockDatabaseTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @MockitoBean
    private InvoiceRepository invoiceRepository;

    @MockitoBean
    private PaymentRepository paymentRepository;

    @MockitoBean
    private ExpenseRepository expenseRepository;

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean(answers = Answers.RETURNS_DEEP_STUBS)
    private MongoDatabaseFactory mongoDatabaseFactory;

    // ----- fixtures ------------------------------------------------------

    private String tokenFor(String role) {
        User user = new User();
        user.setEmail("pm@example.com");
        user.setPassword("{noop}unused");
        user.setRole(role);
        user.setEnabled(true);
        when(userRepository.findByEmailIgnoreCase("pm@example.com"))
                .thenReturn(Optional.of(user));
        return jwtService.generateToken("pm@example.com", role);
    }

    private Invoice invoice() {
        LocalDate now = LocalDate.now();
        Invoice invoice = new Invoice();
        invoice.setId("inv-1");
        invoice.setInvoiceNumber("INV-2026-0001");
        invoice.setClientId("client-1");
        invoice.setStatus("SENT");
        invoice.setIssueDate(now.minusDays(5));
        invoice.setDueDate(now.plusDays(10));
        InvoiceLineItem item = new InvoiceLineItem();
        item.setDescription("Work");
        item.setQuantity(new BigDecimal("1"));
        item.setUnitPrice(new BigDecimal("1000"));
        invoice.setLineItems(List.of(item));
        invoice.setCreatedAt(Instant.parse("2024-01-01T00:00:00Z"));
        return invoice;
    }

    private Payment payment() {
        Payment payment = new Payment();
        payment.setId("pay-1");
        payment.setInvoiceId("inv-1");
        payment.setAmount(new BigDecimal("400.00"));
        payment.setPaymentDate(LocalDate.now().minusDays(2));
        return payment;
    }

    private Expense expense() {
        Expense expense = new Expense();
        expense.setId("exp-1");
        expense.setTitle("Flights");
        expense.setCategory("TRAVEL");
        expense.setStatus("APPROVED");
        expense.setAmount(new BigDecimal("150.00"));
        expense.setExpenseDate(LocalDate.now().minusDays(2));
        return expense;
    }

    private void stubSummary() {
        when(invoiceRepository.findAllByOrderByCreatedAtDesc())
                .thenReturn(List.of(invoice()));
        when(paymentRepository.findAllByOrderByCreatedAtDesc())
                .thenReturn(List.of(payment()));
        when(expenseRepository.findAllByOrderByCreatedAtDesc())
                .thenReturn(List.of(expense()));
        when(paymentRepository.findByInvoiceIdIn(any()))
                .thenReturn(List.of(payment()));
    }

    // ----- tests ---------------------------------------------------------

    @Test
    void summaryRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/finance/summary"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void summaryReturnsComputedFigures() throws Exception {
        stubSummary();

        mockMvc.perform(get("/api/finance/summary")
                        .header("Authorization", "Bearer " + tokenFor("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.summary.totalInvoiced").value(1000.0))
                .andExpect(jsonPath("$.summary.totalCollected").value(400.0))
                .andExpect(jsonPath("$.summary.totalOutstanding").value(600.0))
                .andExpect(jsonPath("$.summary.approvedExpenseTotal")
                        .value(150.0))
                .andExpect(jsonPath("$.summary.netProfit").value(250.0));
    }

    @Test
    void exportReturnsCsv() throws Exception {
        stubSummary();

        String csv = mockMvc.perform(get("/api/finance/export")
                        .header("Authorization", "Bearer " + tokenFor("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.parseMediaType("text/csv")))
                .andReturn().getResponse().getContentAsString();

        assertTrue(csv.contains("Net profit"));
        assertTrue(csv.contains("Month"));
    }
}
