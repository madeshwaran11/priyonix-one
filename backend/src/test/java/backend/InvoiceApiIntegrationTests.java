package backend;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
 * End-to-end tests for the Invoices API through the real Spring Security
 * filter chain: JWT authentication, the ADMIN-only DELETE rule, validation,
 * reference checks, derived totals and the CSV export.
 */
@SpringBootTest(properties = {
        "spring.mongodb.uri=mongodb://127.0.0.1:27017/priyonix-test",
        "JWT_SECRET=dGVzdC1zZWNyZXQtZm9yLXVuaXQtYnVpbGRzLW9ubHktMzItYnl0ZXM=",
        "ADMIN_EMAIL=",
        "ADMIN_PASSWORD="
})
@AutoConfigureMockMvc
class InvoiceApiIntegrationTests extends PlatformMockDatabaseTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @MockitoBean
    private InvoiceRepository invoiceRepository;

    @MockitoBean
    private PaymentRepository paymentRepository;

    @MockitoBean
    private ClientRepository clientRepository;

    @MockitoBean
    private ProjectRepository projectRepository;

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
        client.setCompanyName("Northwind Ltd");
        client.setEmail("hello@northwind.com");
        client.setStatus("ACTIVE");
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
        item.setDescription("Design");
        item.setQuantity(new BigDecimal("2"));
        item.setUnitPrice(new BigDecimal("1000"));
        invoice.setLineItems(List.of(item));
        invoice.setCreatedAt(Instant.parse("2024-01-01T00:00:00Z"));
        return invoice;
    }

    private String invoiceJson(
            String clientId, String issueDate, String dueDate, String status) {
        return "{"
                + "\"clientId\":\"" + clientId + "\","
                + "\"issueDate\":\"" + issueDate + "\","
                + "\"dueDate\":\"" + dueDate + "\","
                + "\"status\":\"" + status + "\","
                + "\"lineItems\":["
                + "{\"description\":\"Design\",\"quantity\":2,"
                + "\"unitPrice\":1000}"
                + "],"
                + "\"taxRate\":18,"
                + "\"discount\":0,"
                + "\"notes\":\"First invoice\""
                + "}";
    }

    // ----- authentication ------------------------------------------------

    @Test
    void listRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/invoices"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createRequiresAuthentication() throws Exception {
        mockMvc.perform(post("/api/invoices")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invoiceJson("client-1", "2026-01-15",
                                "2026-02-15", "SENT")))
                .andExpect(status().isUnauthorized());
    }

    // ----- create --------------------------------------------------------

    @Test
    void createReturnsComputedInvoice() throws Exception {
        when(legacyIntegrityService.nextInvoiceNumber())
                .thenReturn("INV-" + LocalDate.now().getYear() + "-0001");
        when(clientRepository.findById("client-1"))
                .thenReturn(Optional.of(sampleClient()));
        when(invoiceRepository.countByInvoiceNumberStartingWith(anyString()))
                .thenReturn(0L);
        when(invoiceRepository.existsByInvoiceNumberIgnoreCase(anyString()))
                .thenReturn(false);
        when(invoiceRepository.save(any(Invoice.class)))
                .thenAnswer(invocation -> {
                    Invoice invoice = invocation.getArgument(0);
                    invoice.setId("inv-new");
                    return invoice;
                });
        when(paymentRepository.findByInvoiceIdIn(any()))
                .thenReturn(List.of());
        when(clientRepository.findAllById(any()))
                .thenReturn(List.of(sampleClient()));

        mockMvc.perform(post("/api/invoices")
                        .header("Authorization", "Bearer " + tokenFor("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invoiceJson("client-1",
                                LocalDate.now().toString(),
                                LocalDate.now().plusDays(30).toString(),
                                "SENT")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.invoice.id").value("inv-new"))
                .andExpect(jsonPath("$.invoice.invoiceNumber")
                        .value("INV-" + LocalDate.now().getYear() + "-0001"))
                .andExpect(jsonPath("$.invoice.clientName")
                        .value("Northwind Traders"))
                .andExpect(jsonPath("$.invoice.status").value("SENT"))
                .andExpect(jsonPath("$.invoice.subtotal").value(2000.0))
                .andExpect(jsonPath("$.invoice.taxAmount").value(360.0))
                .andExpect(jsonPath("$.invoice.total").value(2360.0))
                .andExpect(jsonPath("$.invoice.balance").value(2360.0));
    }

    @Test
    void createRejectsUnknownClientReference() throws Exception {
        when(clientRepository.findById("ghost")).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/invoices")
                        .header("Authorization", "Bearer " + tokenFor("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invoiceJson("ghost", "2026-01-15",
                                "2026-02-15", "SENT")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("ERROR"));
    }

    @Test
    void createRejectsDueDateBeforeIssueDate() throws Exception {
        when(clientRepository.findById("client-1"))
                .thenReturn(Optional.of(sampleClient()));

        mockMvc.perform(post("/api/invoices")
                        .header("Authorization", "Bearer " + tokenFor("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invoiceJson("client-1", "2026-02-15",
                                "2026-01-15", "SENT")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("ERROR"));
    }

    @Test
    void createRejectsMissingLineItems() throws Exception {
        String body = "{"
                + "\"clientId\":\"client-1\","
                + "\"issueDate\":\"2026-01-15\","
                + "\"dueDate\":\"2026-02-15\""
                + "}";

        mockMvc.perform(post("/api/invoices")
                        .header("Authorization", "Bearer " + tokenFor("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.lineItems").exists());
    }

    // ----- list ----------------------------------------------------------

    @Test
    void listDerivesPaidStatusFromPayments() throws Exception {
        Invoice invoice = sampleInvoice();
        invoice.setId("inv-paid");

        Payment payment = new Payment();
        payment.setId("pay-1");
        payment.setInvoiceId("inv-paid");
        payment.setAmount(new BigDecimal("2000.00"));

        when(invoiceRepository.findAllByOrderByCreatedAtDesc())
                .thenReturn(List.of(invoice));
        when(paymentRepository.findByInvoiceIdIn(any()))
                .thenReturn(List.of(payment));
        when(clientRepository.findAllById(any()))
                .thenReturn(List.of(sampleClient()));

        mockMvc.perform(get("/api/invoices")
                        .header("Authorization", "Bearer " + tokenFor("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(1))
                .andExpect(jsonPath("$.invoices[0].status").value("PAID"))
                .andExpect(jsonPath("$.invoices[0].amountPaid").value(2000.0))
                .andExpect(jsonPath("$.invoices[0].balance").value(0.0));
    }

    // ----- export --------------------------------------------------------

    @Test
    void exportReturnsCsv() throws Exception {
        when(invoiceRepository.findAllByOrderByCreatedAtDesc())
                .thenReturn(List.of(sampleInvoice()));
        when(paymentRepository.findByInvoiceIdIn(any()))
                .thenReturn(List.of());
        when(clientRepository.findAllById(any()))
                .thenReturn(List.of(sampleClient()));

        String csv = mockMvc.perform(get("/api/invoices/export")
                        .header("Authorization", "Bearer " + tokenFor("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.parseMediaType("text/csv")))
                .andReturn().getResponse().getContentAsString();

        assertTrue(csv.contains("Invoice Number"));
        assertTrue(csv.contains("INV-2026-0001"));
    }

    // ----- delete + role checks -----------------------------------------

    @Test
    void deleteForbiddenForNonAdmin() throws Exception {
        mockMvc.perform(delete("/api/invoices/inv-1")
                        .header("Authorization", "Bearer " + tokenFor("USER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void deleteAllowedForAdmin() throws Exception {
        when(invoiceRepository.findById("inv-1"))
                .thenReturn(Optional.of(sampleInvoice()));
        when(paymentRepository.countByInvoiceId("inv-1")).thenReturn(0L);

        mockMvc.perform(delete("/api/invoices/inv-1")
                        .header("Authorization", "Bearer " + tokenFor("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"));

        verify(invoiceRepository).delete(any(Invoice.class));
    }

    @Test
    void deleteWithPaymentsRequiresCascade() throws Exception {
        when(invoiceRepository.findById("inv-1"))
                .thenReturn(Optional.of(sampleInvoice()));
        when(paymentRepository.countByInvoiceId("inv-1")).thenReturn(2L);

        mockMvc.perform(delete("/api/invoices/inv-1")
                        .header("Authorization", "Bearer " + tokenFor("ADMIN")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value("ERROR"));
    }
}
