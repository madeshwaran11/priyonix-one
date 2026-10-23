package backend;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Client document stored in the MongoDB collection "clients".
 *
 * email is a unique business key (stored normalized) so a lead can be
 * converted into a client without ever creating a duplicate. The account
 * owner is stored as a stable {@link Employee} id reference; display names are
 * resolved at read time by {@link ClientService}.
 */
@Document(collection = "clients")
public class Client {

    @Id
    private String id;

    private String name;
    private String companyName;

    @Indexed(unique = true)
    private String email;

    private String phone;
    private String address;
    private String industry;

    /** ACTIVE | PROSPECT | INACTIVE | CHURNED */
    private String status;

    /** Employee id of the account owner (may be null/unassigned). */
    private String assignedToId;

    private String notes;

    /** Id of the lead this client was converted from (null if created directly). */
    private String sourceLeadId;

    private Instant createdAt;
    private Instant updatedAt;

    public Client() {
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

    public String getCompanyName() {
        return companyName;
    }

    public void setCompanyName(String companyName) {
        this.companyName = companyName;
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

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getIndustry() {
        return industry;
    }

    public void setIndustry(String industry) {
        this.industry = industry;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getAssignedToId() {
        return assignedToId;
    }

    public void setAssignedToId(String assignedToId) {
        this.assignedToId = assignedToId;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public String getSourceLeadId() {
        return sourceLeadId;
    }

    public void setSourceLeadId(String sourceLeadId) {
        this.sourceLeadId = sourceLeadId;
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
