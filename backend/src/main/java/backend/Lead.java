package backend;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Lead document stored in the MongoDB collection "leads".
 *
 * A lead moves through the sales pipeline NEW -> CONTACTED -> QUALIFIED ->
 * PROPOSAL -> WON / LOST. A WON lead can be converted into a client exactly
 * once: convertedClientId records the resulting client and guards against a
 * second conversion.
 */
@Document(collection = "leads")
public class Lead {

    @Id
    private String id;

    private String name;
    private String company;
    private String email;
    private String phone;

    /** Where the lead came from (free text, e.g. Website, Referral). */
    private String source;

    /** NEW | CONTACTED | QUALIFIED | PROPOSAL | WON | LOST */
    private String status;

    /** LOW | MEDIUM | HIGH | CRITICAL */
    private String priority;

    /** Employee id the lead is assigned to (may be null/unassigned). */
    private String assignedToId;

    /** Marketing campaign that attributed this lead (null for direct leads). */
    private String campaignId;

    private BigDecimal estimatedValue;

    private LocalDate followUpDate;
    private String notes;

    /** Id of the client this lead was converted into (null until converted). */
    private String convertedClientId;
    private Instant convertedAt;

    private Instant createdAt;
    private Instant updatedAt;

    public Lead() {
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCompany() {
        return company;
    }

    public void setCompany(String company) {
        this.company = company;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getPriority() {
        return priority;
    }

    public void setPriority(String priority) {
        this.priority = priority;
    }

    public String getAssignedToId() {
        return assignedToId;
    }

    public void setAssignedToId(String assignedToId) {
        this.assignedToId = assignedToId;
    }

    public String getCampaignId() {
        return campaignId;
    }

    public void setCampaignId(String campaignId) {
        this.campaignId = campaignId;
    }

    public BigDecimal getEstimatedValue() {
        return estimatedValue;
    }

    public void setEstimatedValue(BigDecimal estimatedValue) {
        this.estimatedValue = estimatedValue;
    }

    public LocalDate getFollowUpDate() {
        return followUpDate;
    }

    public void setFollowUpDate(LocalDate followUpDate) {
        this.followUpDate = followUpDate;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public String getConvertedClientId() {
        return convertedClientId;
    }

    public void setConvertedClientId(String convertedClientId) {
        this.convertedClientId = convertedClientId;
    }

    public Instant getConvertedAt() {
        return convertedAt;
    }

    public void setConvertedAt(Instant convertedAt) {
        this.convertedAt = convertedAt;
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
