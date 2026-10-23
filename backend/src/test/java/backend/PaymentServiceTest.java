package backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private InvoiceRepository invoiceRepository;

    @Mock
    private ClientRepository clientRepository;

    @Mock
    private InvoiceService invoiceService;

    private PaymentService paymentService;

    @BeforeEach
    void setUp() {
        paymentService = new PaymentService(
                paymentRepository, invoiceRepository, clientRepository,
                invoiceService);
    }

    // ----- fixtures ------------------------------------------------------

    private Client client(String id) {
        Client client = new Client();
        client.setId(id);
        client.setName("Northwind Traders");
        return client;
    }

    private Invoice invoice(String id, String status, String amount) {
        Invoice invoice = new Invoice();
        invoice.setId(id);
        invoice.setInvoiceNumber("INV-2024-0001");
        invoice.setClientId("client-1");
        invoice.setStatus(status);
        invoice.setIssueDate(LocalDate.now().minusDays(5));
        invoice.setDueDate(LocalDate.now().plusDays(10));
        InvoiceLineItem item = new InvoiceLineItem();
        item.setDescription("Work");
        item.setQuantity(new BigDecimal("1"));
        item.setUnitPrice(new BigDecimal(amount));
        invoice.setLineItems(List.of(item));
        invoice.setCreatedAt(Instant.parse("2024-01-01T00:00:00Z"));
        return invoice;
    }

    private PaymentRequest request(
            String invoiceId, String amount, String method, String reference) {
        return new PaymentRequest(
                invoiceId, new BigDecimal(amount), LocalDate.now(), method,
                reference, "Note");
    }

    // ----- create --------------------------------------------------------

    @Test
    void createRecordsPaymentWithinBalance() {
        Invoice invoice = invoice("inv-1", "SENT", "100");

        when(invoiceService.getEntity("inv-1")).thenReturn(invoice);
        when(invoiceService.amountPaid("inv-1"))
                .thenReturn(new BigDecimal("0.00"));
        when(paymentRepository.existsByReferenceNumberIgnoreCase("UTR-1"))
                .thenReturn(false);
        when(paymentRepository.save(any(Payment.class)))
                .thenAnswer(invocation -> {
                    Payment payment = invocation.getArgument(0);
                    payment.setId("pay-new");
                    return payment;
                });
        when(invoiceRepository.findAllById(any()))
                .thenReturn(List.of(invoice));
        when(clientRepository.findAllById(any()))
                .thenReturn(List.of(client("client-1")));

        PaymentResponse response = paymentService.create(
                request("inv-1", "50", "BANK_TRANSFER", "UTR-1"),
                "pm@example.com");

        assertEquals(new BigDecimal("50.00"), response.amount());
        assertEquals("INV-2024-0001", response.invoiceNumber());
        assertEquals("Northwind Traders", response.clientName());
        assertEquals("BANK_TRANSFER", response.method());
        assertEquals("pm@example.com", response.recordedBy());
    }

    @Test
    void createRejectsDraftInvoice() {
        when(invoiceService.getEntity("inv-1"))
                .thenReturn(invoice("inv-1", "DRAFT", "100"));

        assertThrows(InvalidRequestException.class,
                () -> paymentService.create(
                        request("inv-1", "10", "CASH", null),
                        "pm@example.com"));

        verify(paymentRepository, never()).save(any(Payment.class));
    }

    @Test
    void createRejectsOverpayment() {
        when(invoiceService.getEntity("inv-1"))
                .thenReturn(invoice("inv-1", "SENT", "100"));
        when(invoiceService.amountPaid("inv-1"))
                .thenReturn(new BigDecimal("90.00"));

        assertThrows(InvalidRequestException.class,
                () -> paymentService.create(
                        request("inv-1", "20", "CASH", null),
                        "pm@example.com"));

        verify(paymentRepository, never()).save(any(Payment.class));
    }

    @Test
    void createRejectsDuplicateReference() {
        when(invoiceService.getEntity("inv-1"))
                .thenReturn(invoice("inv-1", "SENT", "100"));
        when(invoiceService.amountPaid("inv-1"))
                .thenReturn(new BigDecimal("0.00"));
        when(paymentRepository.existsByReferenceNumberIgnoreCase("UTR-1"))
                .thenReturn(true);

        assertThrows(DuplicateResourceException.class,
                () -> paymentService.create(
                        request("inv-1", "10", "CASH", "UTR-1"),
                        "pm@example.com"));
    }

    // ----- update --------------------------------------------------------

    @Test
    void updateRejectsOverpaymentAccountingForEditedPayment() {
        Payment existing = new Payment();
        existing.setId("pay-1");
        existing.setInvoiceId("inv-1");
        existing.setAmount(new BigDecimal("50.00"));

        when(paymentRepository.findById("pay-1"))
                .thenReturn(Optional.of(existing));
        when(invoiceService.getEntity("inv-1"))
                .thenReturn(invoice("inv-1", "SENT", "100"));
        // 50 already applied by this payment + 50 by another = 100 paid.
        when(invoiceService.amountPaid("inv-1"))
                .thenReturn(new BigDecimal("100.00"));

        assertThrows(InvalidRequestException.class,
                () -> paymentService.update(
                        "pay-1", request("inv-1", "90", "CASH", null)));

        verify(paymentRepository, never()).save(any(Payment.class));
    }

    // ----- queries / delete ---------------------------------------------

    @Test
    void listFiltersByMethod() {
        Payment card = new Payment();
        card.setId("pay-card");
        card.setInvoiceId("inv-1");
        card.setAmount(new BigDecimal("10.00"));
        card.setMethod("CARD");
        card.setPaymentDate(LocalDate.now());

        Payment cash = new Payment();
        cash.setId("pay-cash");
        cash.setInvoiceId("inv-1");
        cash.setAmount(new BigDecimal("5.00"));
        cash.setMethod("CASH");
        cash.setPaymentDate(LocalDate.now());

        when(paymentRepository.findAllByOrderByCreatedAtDesc())
                .thenReturn(List.of(card, cash));
        when(invoiceRepository.findAllById(any()))
                .thenReturn(List.of(invoice("inv-1", "SENT", "100")));
        when(clientRepository.findAllById(any()))
                .thenReturn(List.of(client("client-1")));

        PagedResult<PaymentResponse> result =
                paymentService.list(null, null, "CARD", null, null, 1, 20);

        assertEquals(1, result.total());
        assertEquals("pay-card", result.items().get(0).id());
    }

    @Test
    void deleteRejectsUnknownPayment() {
        when(paymentRepository.findById("missing"))
                .thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> paymentService.delete("missing"));
    }
}
