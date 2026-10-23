package backend;

import java.time.Instant;
import java.time.LocalDate;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Follow-up activity stored in the MongoDB collection "followups".
 *
 * A follow-up is linked to exactly one of a lead or a client (enforced by
 * {@link FollowUpService}) and carries an activity type, due date, free-text
 * notes, a completion flag and an optional assigned employee reference.
 * "Overdue" means a due date before today that is not yet completed.
 */
@Document(collection = "followups")
public class FollowUp {

    @Id
    private String id;

    /** CALL | EMAIL | MEETING | DEMO | REMINDER | OTHER */
    private String activityType;

    private LocalDate dueDate;
    private String notes;
    private boolean completed;

    /** Employee id the activity is assigned to (may be null/unassigned). */
    private String assignedToId;

    /** Id of the lead this activity belongs to (null for client activities). */
    private String leadId;

    /** Id of the client this activity belongs to (null for lead activities). */
    private String clientId;

    private Instant createdAt;
    private Instant updatedAt;

    public FollowUp() {
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getActivityType() {
        return activityType;
    }

    public void setActivityType(String activityType) {
        this.activityType = activityType;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public void setDueDate(LocalDate dueDate) {
        this.dueDate = dueDate;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public boolean isCompleted() {
        return completed;
    }

    public void setCompleted(boolean completed) {
        this.completed = completed;
    }

    public String getAssignedToId() {
        return assignedToId;
    }

    public void setAssignedToId(String assignedToId) {
        this.assignedToId = assignedToId;
    }

    public String getLeadId() {
        return leadId;
    }

    public void setLeadId(String leadId) {
        this.leadId = leadId;
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
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
