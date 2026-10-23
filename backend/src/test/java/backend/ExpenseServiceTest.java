package backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ExpenseServiceTest {

    @Mock
    private ExpenseRepository expenseRepository;

    private ExpenseService expenseService;

    @BeforeEach
    void setUp() {
        expenseService = new ExpenseService(expenseRepository);
    }

    // ----- fixtures ------------------------------------------------------

    private ExpenseRequest request(
            String title, String category, String amount, String method) {
        return new ExpenseRequest(
                title, category, new BigDecimal(amount), LocalDate.now(),
                "Acme Supplies", method, "RCPT-1", "Note");
    }

    private Expense storedExpense(
            String id, String status, String category, String amount) {
        Expense expense = new Expense();
        expense.setId(id);
        expense.setTitle("Flights");
        expense.setCategory(category);
        expense.setAmount(new BigDecimal(amount));
        expense.setExpenseDate(LocalDate.now());
        expense.setStatus(status);
        expense.setCreatedAt(Instant.parse("2024-01-01T00:00:00Z"));
        return expense;
    }

    // ----- create --------------------------------------------------------

    @Test
    void createDefaultsToPendingAndNormalizesFields() {
        when(expenseRepository.save(any(Expense.class)))
                .thenAnswer(invocation -> {
                    Expense expense = invocation.getArgument(0);
                    expense.setId("exp-new");
                    return expense;
                });

        ExpenseResponse response = expenseService.create(
                request("Flights to Delhi", "travel", "1250.555", "card"),
                "pm@example.com");

        assertEquals("PENDING", response.status());
        assertEquals("TRAVEL", response.category());
        assertEquals("CARD", response.paymentMethod());
        assertEquals(new BigDecimal("1250.56"), response.amount());
        assertEquals("pm@example.com", response.createdBy());
    }

    @Test
    void createRejectsInvalidCategory() {
        assertThrows(InvalidRequestException.class,
                () -> expenseService.create(
                        request("Flights", "SPACESHIP", "10", "CASH"),
                        "pm@example.com"));

        verify(expenseRepository, never()).save(any(Expense.class));
    }

    @Test
    void createRejectsInvalidPaymentMethod() {
        assertThrows(InvalidRequestException.class,
                () -> expenseService.create(
                        request("Flights", "TRAVEL", "10", "CRYPTO"),
                        "pm@example.com"));

        verify(expenseRepository, never()).save(any(Expense.class));
    }

    // ----- update --------------------------------------------------------

    @Test
    void updateRejectsApprovedExpense() {
        Expense existing = storedExpense("exp-1", "APPROVED", "TRAVEL", "100.00");
        when(expenseRepository.findById("exp-1")).thenReturn(Optional.of(existing));
        assertThrows(ConflictException.class, () -> expenseService.update("exp-1", request("Revised", "TRAVEL", "150", "CASH")));
        verify(expenseRepository, never()).save(any(Expense.class));
    }

    // ----- status decisions ---------------------------------------------

    @Test
    void updateStatusApprovesAndRecordsActor() {
        Expense existing = storedExpense(
                "exp-1", "PENDING", "TRAVEL", "100.00");

        when(expenseRepository.findById("exp-1"))
                .thenReturn(Optional.of(existing));
        when(expenseRepository.save(any(Expense.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ExpenseResponse response = expenseService.updateStatus(
                "exp-1", "approved", "admin@example.com");

        assertEquals("APPROVED", response.status());
        assertEquals("admin@example.com", response.approvedBy());
        assertNotNull(response.approvedAt());
    }

    @Test
    void approvedExpenseCannotReturnToPending() {
        Expense existing = storedExpense("exp-1", "APPROVED", "TRAVEL", "100.00");
        when(expenseRepository.findById("exp-1")).thenReturn(Optional.of(existing));
        assertThrows(InvalidRequestException.class, () -> expenseService.updateStatus("exp-1", "PENDING", "admin@x"));
        verify(expenseRepository, never()).save(any(Expense.class));
    }

    @Test
    void updateStatusRejectsInvalidStatus() {
        when(expenseRepository.findById("exp-1"))
                .thenReturn(Optional.of(storedExpense(
                        "exp-1", "PENDING", "TRAVEL", "100.00")));

        assertThrows(InvalidRequestException.class,
                () -> expenseService.updateStatus(
                        "exp-1", "MAYBE", "admin@x"));

        verify(expenseRepository, never()).save(any(Expense.class));
    }

    // ----- queries / delete ---------------------------------------------

    @Test
    void listFiltersByStatusAndCategory() {
        Expense travelPending = storedExpense(
                "exp-1", "PENDING", "TRAVEL", "100.00");
        Expense softwareApproved = storedExpense(
                "exp-2", "APPROVED", "SOFTWARE", "200.00");

        when(expenseRepository.findAllByOrderByCreatedAtDesc())
                .thenReturn(List.of(travelPending, softwareApproved));

        PagedResult<ExpenseResponse> pending = expenseService.list(
                null, "PENDING", null, null, null, 1, 20);
        assertEquals(1, pending.total());
        assertEquals("exp-1", pending.items().get(0).id());

        PagedResult<ExpenseResponse> travel = expenseService.list(
                null, null, "TRAVEL", null, null, 1, 20);
        assertEquals(1, travel.total());
        assertEquals("exp-1", travel.items().get(0).id());
    }

    @Test
    void deleteRejectsUnknownExpense() {
        when(expenseRepository.findById("missing"))
                .thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> expenseService.delete("missing"));
    }
}
