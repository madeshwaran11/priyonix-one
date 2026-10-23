package backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InvoiceServiceTest {

    @Mock
    private InvoiceRepository invoiceRepository;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private ClientRepository clientRepository;

    @Mock
    private ProjectRepository projectRepository;

    private InvoiceService invoiceService;

    @BeforeEach
    void setUp() {
        invoiceService = new InvoiceService(
                invoiceRepository, paymentRepository, clientRepository,
                projectRepository);
    }

    // ----- fixtures ------------------------------------------------------

    private Client client(String id) {
        Client client = new Client();
        client.setId(id);
        client.setName("Northwind Traders");
        client.setCompanyName("Northwind Ltd");
        return client;
    }

    private Project project(String id) {
        Project project = new Project();
        project.setId(id);
        project.setProjectCode("PRJ-001");
        project.setName("Website rebuild");
        return project;
    }

    private InvoiceLineItemRequest line(
            String description, String quantity, String unitPrice) {
        return new InvoiceLineItemRequest(
                description, new BigDecimal(quantity),
                new BigDecimal(unitPrice));
    }

    private InvoiceRequest request(
            String clientId, String projectId, LocalDate issue,
            LocalDate due, String status, List<InvoiceLineItemRequest> lines,
            String taxRate, String discount) {
        return new InvoiceRequest(
                clientId, projectId, issue, due, status, lines,
                taxRate == null ? null : new BigDecimal(taxRate),
                discount == null ? null : new BigDecimal(discount),
                "Note");
    }

    private Invoice storedInvoice(String id, String status, LocalDate dueDate,
            String quantity, String unitPrice) {
        Invoice invoice = new Invoice();
        invoice.setId(id);
        invoice.setInvoiceNumber("INV-2024-0001");
        invoice.setClientId("client-1");
        invoice.setStatus(status);
        invoice.setIssueDate(LocalDate.now().minusDays(10));
        invoice.setDueDate(dueDate);
        InvoiceLineItem item = new InvoiceLineItem();
        item.setDescription("Work");
        item.setQuantity(new BigDecimal(quantity));
        item.setUnitPrice(new BigDecimal(unitPrice));
        invoice.setLineItems(List.of(item));
        invoice.setCreatedAt(Instant.parse("2024-01-01T00:00:00Z"));
        return invoice;
    }

    private Payment payment(String id, String invoiceId, String amount) {
        Payment payment = new Payment();
        payment.setId(id);
        payment.setInvoiceId(invoiceId);
        payment.setAmount(new BigDecimal(amount));
        return payment;
    }

    // ----- create --------------------------------------------------------

    @Test
    void createComputesTotalsAndGeneratesNumber() {
        when(clientRepository.findById("client-1"))
                .thenReturn(Optional.of(client("client-1")));
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
                .thenReturn(List.of(client("client-1")));

        InvoiceResponse response = invoiceService.create(
                request("client-1", null, LocalDate.now(),
                        LocalDate.now().plusDays(30), "SENT",
                        List.of(line("Design", "2", "1000"),
                                line("Build", "3.5", "200")),
                        "18", "100"),
                "pm@example.com");

        assertEquals(new BigDecimal("2700.00"), response.subtotal());
        assertEquals(new BigDecimal("100.00"), response.discount());
        assertEquals(new BigDecimal("2600.00"), response.taxableAmount());
        assertEquals(new BigDecimal("468.00"), response.taxAmount());
        assertEquals(new BigDecimal("3068.00"), response.total());
        assertEquals(new BigDecimal("0.00"), response.amountPaid());
        assertEquals(new BigDecimal("3068.00"), response.balance());
        assertEquals("SENT", response.status());
        assertTrue(response.invoiceNumber().startsWith("INV-"));

        ArgumentCaptor<Invoice> captor = ArgumentCaptor.forClass(Invoice.class);
        verify(invoiceRepository).save(captor.capture());
        assertEquals("pm@example.com", captor.getValue().getCreatedBy());
        assertEquals(2, captor.getValue().getLineItems().size());
    }

    @Test
    void createRejectsUnknownClient() {
        when(clientRepository.findById("ghost")).thenReturn(Optional.empty());

        assertThrows(InvalidRequestException.class,
                () -> invoiceService.create(
                        request("ghost", null, LocalDate.now(),
                                LocalDate.now(), "DRAFT",
                                List.of(line("Work", "1", "10")), null, null),
                        "pm@example.com"));

        verify(invoiceRepository, never()).save(any(Invoice.class));
    }

    @Test
    void createRejectsUnknownProject() {
        when(clientRepository.findById("client-1"))
                .thenReturn(Optional.of(client("client-1")));
        when(projectRepository.findById("ghost")).thenReturn(Optional.empty());

        assertThrows(InvalidRequestException.class,
                () -> invoiceService.create(
                        request("client-1", "ghost", LocalDate.now(),
                                LocalDate.now(), "DRAFT",
                                List.of(line("Work", "1", "10")), null, null),
                        "pm@example.com"));
    }

    @Test
    void createRejectsDueDateBeforeIssueDate() {
        when(clientRepository.findById("client-1"))
                .thenReturn(Optional.of(client("client-1")));

        assertThrows(InvalidRequestException.class,
                () -> invoiceService.create(
                        request("client-1", null, LocalDate.now(),
                                LocalDate.now().minusDays(1), "DRAFT",
                                List.of(line("Work", "1", "10")), null, null),
                        "pm@example.com"));
    }

    @Test
    void createRejectsDiscountGreaterThanSubtotal() {
        when(clientRepository.findById("client-1"))
                .thenReturn(Optional.of(client("client-1")));

        assertThrows(InvalidRequestException.class,
                () -> invoiceService.create(
                        request("client-1", null, LocalDate.now(),
                                LocalDate.now(), "DRAFT",
                                List.of(line("Work", "1", "10")), null, "50"),
                        "pm@example.com"));
    }

    @Test
    void createRejectsDerivedStatus() {
        when(clientRepository.findById("client-1"))
                .thenReturn(Optional.of(client("client-1")));

        assertThrows(InvalidRequestException.class,
                () -> invoiceService.create(
                        request("client-1", null, LocalDate.now(),
                                LocalDate.now(), "PAID",
                                List.of(line("Work", "1", "10")), null, null),
                        "pm@example.com"));

        verify(invoiceRepository, never()).save(any(Invoice.class));
    }

    // ----- update --------------------------------------------------------

    @Test
    void updatePreservesNumberAndCreator() {
        Invoice existing = storedInvoice("inv-1", "SENT",
                LocalDate.now().plusDays(5), "1", "100");
        existing.setCreatedBy("original@example.com");

        when(invoiceRepository.findById("inv-1"))
                .thenReturn(Optional.of(existing));
        when(clientRepository.findById("client-1"))
                .thenReturn(Optional.of(client("client-1")));
        when(invoiceRepository.save(any(Invoice.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(paymentRepository.findByInvoiceIdIn(any()))
                .thenReturn(List.of());
        when(clientRepository.findAllById(any()))
                .thenReturn(List.of(client("client-1")));

        invoiceService.update("inv-1",
                request("client-1", null, LocalDate.now(),
                        LocalDate.now().plusDays(5), "SENT",
                        List.of(line("Work", "1", "250")), null, null));

        ArgumentCaptor<Invoice> captor = ArgumentCaptor.forClass(Invoice.class);
        verify(invoiceRepository).save(captor.capture());
        assertEquals("INV-2024-0001", captor.getValue().getInvoiceNumber());
        assertEquals("original@example.com", captor.getValue().getCreatedBy());
        assertEquals(new BigDecimal("250.00"),
                InvoiceService.totals(captor.getValue()).total());
    }

    // ----- status derivation --------------------------------------------

    @Test
    void listDerivesEffectiveStatusFromPayments() {
        Invoice overdue = storedInvoice("inv-overdue", "SENT",
                LocalDate.now().minusDays(3), "1", "100");
        Invoice partial = storedInvoice("inv-partial", "SENT",
                LocalDate.now().plusDays(3), "1", "100");
        Invoice paid = storedInvoice("inv-paid", "SENT",
                LocalDate.now().plusDays(3), "1", "100");
        Invoice draft = storedInvoice("inv-draft", "DRAFT",
                LocalDate.now().plusDays(3), "1", "100");

        when(invoiceRepository.findAllByOrderByCreatedAtDesc())
                .thenReturn(List.of(overdue, partial, paid, draft));
        when(paymentRepository.findByInvoiceIdIn(any()))
                .thenReturn(List.of(
                        payment("pay-1", "inv-partial", "40"),
                        payment("pay-2", "inv-paid", "100")));
        when(clientRepository.findAllById(any()))
                .thenReturn(List.of(client("client-1")));

        PagedResult<InvoiceResponse> result =
                invoiceService.list(null, null, null, null, false, 1, 20);

        assertEquals(4, result.total());
        assertEquals("OVERDUE", statusOf(result, "inv-overdue"));
        assertEquals("PARTIALLY_PAID", statusOf(result, "inv-partial"));
        assertEquals("PAID", statusOf(result, "inv-paid"));
        assertEquals("DRAFT", statusOf(result, "inv-draft"));

        assertTrue(responseOf(result, "inv-overdue").overdue());
        assertFalse(responseOf(result, "inv-paid").overdue());
    }

    @Test
    void listFiltersByEffectiveStatus() {
        Invoice partial = storedInvoice("inv-partial", "SENT",
                LocalDate.now().plusDays(3), "1", "100");
        Invoice draft = storedInvoice("inv-draft", "DRAFT",
                LocalDate.now().plusDays(3), "1", "100");

        when(invoiceRepository.findAllByOrderByCreatedAtDesc())
                .thenReturn(List.of(partial, draft));
        when(paymentRepository.findByInvoiceIdIn(any()))
                .thenReturn(List.of(payment("pay-1", "inv-partial", "40")));
        when(clientRepository.findAllById(any()))
                .thenReturn(List.of(client("client-1")));

        PagedResult<InvoiceResponse> result =
                invoiceService.list(null, "PARTIALLY_PAID", null, null, false,
                        1, 20);

        assertEquals(1, result.total());
        assertEquals("inv-partial", result.items().get(0).id());
    }

    @Test
    void listRejectsInvalidStatus() {
        assertThrows(InvalidRequestException.class,
                () -> invoiceService.list(null, "NOPE", null, null, false,
                        1, 20));
    }

    // ----- delete --------------------------------------------------------

    @Test
    void deleteRejectsWhenPaymentsExistWithoutCascade() {
        Invoice invoice = storedInvoice("inv-1", "SENT",
                LocalDate.now(), "1", "100");

        when(invoiceRepository.findById("inv-1"))
                .thenReturn(Optional.of(invoice));
        when(paymentRepository.countByInvoiceId("inv-1")).thenReturn(2L);

        assertThrows(ConflictException.class,
                () -> invoiceService.delete("inv-1", false));

        verify(invoiceRepository, never()).delete(any(Invoice.class));
    }

    @Test
    void cascadeCannotErasePaymentHistory() {
        Invoice invoice = storedInvoice("inv-1", "SENT", LocalDate.now(), "1", "100");
        when(invoiceRepository.findById("inv-1")).thenReturn(Optional.of(invoice));
        when(paymentRepository.countByInvoiceId("inv-1")).thenReturn(1L);
        assertThrows(ConflictException.class, () -> invoiceService.delete("inv-1", true));
        verify(paymentRepository, never()).deleteByInvoiceId("inv-1");
        verify(invoiceRepository, never()).delete(invoice);
    }

    @Test
    void deleteRejectsUnknownInvoice() {
        when(invoiceRepository.findById("missing"))
                .thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> invoiceService.delete("missing", false));
    }

    // ----- helpers -------------------------------------------------------

    private String statusOf(PagedResult<InvoiceResponse> result, String id) {
        return responseOf(result, id).status();
    }

    private InvoiceResponse responseOf(
            PagedResult<InvoiceResponse> result, String id) {
        return result.items().stream()
                .filter(item -> item.id().equals(id))
                .findFirst()
                .orElseThrow();
    }
}
