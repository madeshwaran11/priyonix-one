package backend;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Payment document stored in the MongoDB collection "payments".
 *
 * Every payment is tied to exactly one invoice. Invoice balances and statuses
 * are always derived from these records (never stored on the invoice), so a
 * payment can never be double-counted. No card or bank credentials are stored
 * here — only a non-sensitive reference number supplied by the operator.
 */
@Document(collection = "payments")
public class Payment {

    @Id
    private String id;

    /** Id of the invoice this payment is applied to. */
    private String invoiceId;

    private BigDecimal amount;
    private LocalDate paymentDate;

    /** CASH | BANK_TRANSFER | CARD | UPI | CHEQUE | OTHER */
    private String method;

    /**
     * Optional operator-supplied reference (e.g. a bank UTR). Unique when
     * present so the same transaction cannot be recorded twice.
     */
    @Indexed(unique = true, sparse = true)
    private String referenceNumber;

    private String notes;

    /** Email of the authenticated user who recorded the payment. */
    private String recordedBy;

    private Instant createdAt;
    private Instant updatedAt;

    public Payment() {
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getInvoiceId() {
        return invoiceId;
    }

    public void setInvoiceId(String invoiceId) {
        this.invoiceId = invoiceId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public LocalDate getPaymentDate() {
        return paymentDate;
    }

    public void setPaymentDate(LocalDate paymentDate) {
        this.paymentDate = paymentDate;
    }

    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public String getReferenceNumber() {
        return referenceNumber;
    }

    public void setReferenceNumber(String referenceNumber) {
        this.referenceNumber = referenceNumber;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public String getRecordedBy() {
        return recordedBy;
    }

    public void setRecordedBy(String recordedBy) {
        this.recordedBy = recordedBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
