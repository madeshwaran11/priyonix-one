package backend;

import java.math.BigDecimal;

/**
 * A single billing line on an {@link Invoice}.
 *
 * Only the raw inputs are persisted (description, quantity and unit price);
 * the line amount is derived at read time so it can never drift out of sync
 * with the invoice totals.
 */
public class InvoiceLineItem {

    private String description;
    private BigDecimal quantity;
    private BigDecimal unitPrice;

    public InvoiceLineItem() {
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public void setQuantity(BigDecimal quantity) {
        this.quantity = quantity;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public void setUnitPrice(BigDecimal unitPrice) {
        this.unitPrice = unitPrice;
    }
}
