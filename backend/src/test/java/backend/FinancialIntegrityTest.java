package backend;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FinancialIntegrityTest {
    @Test void csvNeutralizesFormulaTextButPreservesNumbers() {
        assertEquals("'=HYPERLINK(\"\"x\"\")", Csv.escape("=HYPERLINK(\"x\")").replaceAll("^\"|\"$", ""));
        assertEquals("-4", Csv.escape(-4));
        assertEquals("'  +cmd", Csv.escape("  +cmd"));
    }
    @Test void extremePaginationReturnsEmptyRatherThanOverflowing() {
        assertTrue(PagedResult.of(List.of("one"), Integer.MAX_VALUE, 100).items().isEmpty());
    }
    @Test void paidInvoiceCannotBeRewritten() {
        var invoices=mock(InvoiceRepository.class); var payments=mock(PaymentRepository.class);
        var service=new InvoiceService(invoices,payments,mock(ClientRepository.class),mock(ProjectRepository.class));
        var invoice=new Invoice();invoice.setId("i");
        when(invoices.findById("i")).thenReturn(Optional.of(invoice));when(payments.countByInvoiceId("i")).thenReturn(1L);
        assertThrows(ConflictException.class,()->service.update("i",null));
    }
    @Test void paidInvoiceCannotBeCascadeDeleted() {
        var invoices=mock(InvoiceRepository.class);var payments=mock(PaymentRepository.class);
        var service=new InvoiceService(invoices,payments,mock(ClientRepository.class),mock(ProjectRepository.class));
        var invoice=new Invoice();invoice.setId("i");
        when(invoices.findById("i")).thenReturn(Optional.of(invoice));when(payments.countByInvoiceId("i")).thenReturn(1L);
        assertThrows(ConflictException.class,()->service.delete("i",true));
        verify(payments,never()).deleteByInvoiceId(anyString());
    }
    @Test void approvedExpensesAreImmutable() {
        var repository=mock(ExpenseRepository.class);var expense=new Expense();expense.setStatus("APPROVED");
        when(repository.findById("e")).thenReturn(Optional.of(expense));
        assertThrows(ConflictException.class,()->new ExpenseService(repository).update("e",null));
    }
    @Test void reimbursementRequiresPriorApproval() {
        var repository=mock(ExpenseRepository.class);var expense=new Expense();expense.setStatus("PENDING");
        when(repository.findById("e")).thenReturn(Optional.of(expense));
        assertThrows(InvalidRequestException.class,()->new ExpenseService(repository).updateStatus("e","REIMBURSED","admin"));
    }
}
