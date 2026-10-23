package backend;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
 * End-to-end tests for the Payments API through the real Spring Security
 * filter chain: JWT authentication, the ADMIN-only DELETE rule, overpayment
 * and duplicate-reference rejection, and invoice enrichment.
 */
@SpringBootTest(properties = {
        "spring.mongodb.uri=mongodb://127.0.0.1:27017/priyonix-test",
        "JWT_SECRET=dGVzdC1zZWNyZXQtZm9yLXVuaXQtYnVpbGRzLW9ubHktMzItYnl0ZXM=",
        "ADMIN_EMAIL=",
        "ADMIN_PASSWORD="
})
@AutoConfigureMockMvc
class PaymentApiIntegrationTests extends PlatformMockDatabaseTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @MockitoBean
    private PaymentRepository paymentRepository;

    @MockitoBean
    private InvoiceRepository invoiceRepository;

    @MockitoBean
    private ClientRepository clientRepository;

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean(answers = Answers.RETURNS_DEEP_STUBS)
    private MongoDatabaseFactory mongoDatabaseFactory;

    // ----- fixtures ------------------------------------------------------

    private User enabledUser(String role) {
        User user = new User();
        user.setEmail("pm@example.com");
        user.setPassword("{noop}unused");
        user.setRole(role);
        user.setEnabled(true);
        return user;
    }

    private String tokenFor(String role) {
        when(userRepository.findByEmailIgnoreCase("pm@example.com"))
                .thenReturn(Optional.of(enabledUser(role)));
        return jwtService.generateToken("pm@example.com", role);
    }

    private Client sampleClient() {
        Client client = new Client();
        client.setId("client-1");
        client.setName("Northwind Traders");
        return client;
    }

    private Invoice sampleInvoice() {
        Invoice invoice = new Invoice();
        invoice.setId("inv-1");
        invoice.setInvoiceNumber("INV-2026-0001");
        invoice.setClientId("client-1");
        invoice.setStatus("SENT");
        invoice.setIssueDate(LocalDate.now().minusDays(5));
        invoice.setDueDate(LocalDate.now().plusDays(10));
        InvoiceLineItem item = new InvoiceLineItem();
        item.setDescription("Work");
        item.setQuantity(new BigDecimal("1"));
        item.setUnitPrice(new BigDecimal("100"));
        invoice.setLineItems(List.of(item));
        invoice.setCreatedAt(Instant.parse("2024-01-01T00:00:00Z"));
        return invoice;
    }

    private String paymentJson(String amount, String reference) {
        return "{"
                + "\"invoiceId\":\"inv-1\","
                + "\"amount\":" + amount + ","
                + "\"paymentDate\":\"2026-01-20\","
                + "\"method\":\"BANK_TRANSFER\","
                + "\"referenceNumber\":" + (reference == null
                        ? "null" : "\"" + reference + "\"") + ","
                + "\"notes\":\"Part payment\""
                + "}";
    }

    // ----- authentication ------------------------------------------------

    @Test
    void listRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/payments"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createRequiresAuthentication() throws Exception {
        mockMvc.perform(post("/api/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(paymentJson("50", null)))
                .andExpect(status().isUnauthorized());
    }

    // ----- create --------------------------------------------------------

    @Test
    void createRecordsPayment() throws Exception {
        when(invoiceRepository.findById("inv-1"))
                .thenReturn(Optional.of(sampleInvoice()));
        when(paymentRepository.findByInvoiceId("inv-1"))
                .thenReturn(List.of());
        when(paymentRepository.existsByReferenceNumberIgnoreCase("UTR-1"))
                .thenReturn(false);
        when(paymentRepository.save(any(Payment.class)))
                .thenAnswer(invocation -> {
                    Payment payment = invocation.getArgument(0);
                    payment.setId("pay-new");
                    return payment;
                });
        when(invoiceRepository.findAllById(any()))
                .thenReturn(List.of(sampleInvoice()));
        when(clientRepository.findAllById(any()))
                .thenReturn(List.of(sampleClient()));

        mockMvc.perform(post("/api/payments")
                        .header("Authorization", "Bearer " + tokenFor("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(paymentJson("50", "UTR-1")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.payment.id").value("pay-new"))
                .andExpect(jsonPath("$.payment.invoiceNumber")
                        .value("INV-2026-0001"))
                .andExpect(jsonPath("$.payment.clientName")
                        .value("Northwind Traders"))
                .andExpect(jsonPath("$.payment.amount").value(50.0));
    }

    @Test
    void createRejectsOverpayment() throws Exception {
        when(invoiceRepository.findById("inv-1"))
                .thenReturn(Optional.of(sampleInvoice()));

        Payment existing = new Payment();
        existing.setId("pay-1");
        existing.setInvoiceId("inv-1");
        existing.setAmount(new BigDecimal("90.00"));

        when(paymentRepository.findByInvoiceId("inv-1"))
                .thenReturn(List.of(existing));

        mockMvc.perform(post("/api/payments")
                        .header("Authorization", "Bearer " + tokenFor("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(paymentJson("20", null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("ERROR"));
    }

    @Test
    void createRejectsDuplicateReference() throws Exception {
        when(invoiceRepository.findById("inv-1"))
                .thenReturn(Optional.of(sampleInvoice()));
        when(paymentRepository.findByInvoiceId("inv-1"))
                .thenReturn(List.of());
        when(paymentRepository.existsByReferenceNumberIgnoreCase("UTR-1"))
                .thenReturn(true);

        mockMvc.perform(post("/api/payments")
                        .header("Authorization", "Bearer " + tokenFor("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(paymentJson("10", "UTR-1")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value("ERROR"));
    }

    @Test
    void createRejectsDraftInvoice() throws Exception {
        Invoice draft = sampleInvoice();
        draft.setStatus("DRAFT");
        when(invoiceRepository.findById("inv-1"))
                .thenReturn(Optional.of(draft));

        mockMvc.perform(post("/api/payments")
                        .header("Authorization", "Bearer " + tokenFor("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(paymentJson("10", null)))
                .andExpect(status().isBadRequest());
    }

    // ----- delete + role checks -----------------------------------------

    @Test
    void deleteForbiddenForNonAdmin() throws Exception {
        mockMvc.perform(delete("/api/payments/pay-1")
                        .header("Authorization", "Bearer " + tokenFor("USER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void deleteAllowedForAdmin() throws Exception {
        Payment payment = new Payment();
        payment.setId("pay-1");
        payment.setInvoiceId("inv-1");
        payment.setAmount(new BigDecimal("50.00"));

        when(paymentRepository.findById("pay-1"))
                .thenReturn(Optional.of(payment));

        mockMvc.perform(delete("/api/payments/pay-1")
                        .header("Authorization", "Bearer " + tokenFor("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"));

        verify(paymentRepository).delete(payment);
    }
}
