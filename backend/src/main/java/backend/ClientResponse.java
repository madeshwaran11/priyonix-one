package backend;

import java.time.Instant;

/**
 * Client representation returned by the API.
 *
 * Includes the stable assigned-employee id (for round-tripping edit forms)
 * plus a resolved {@link EmployeeRef} for display, and live follow-up counts
 * so the UI can highlight overdue work.
 */
public record ClientResponse(
        String id,
        String name,
        String companyName,
        String email,
        String phone,
        String address,
        String industry,
        String status,
        String assignedToId,
        EmployeeRef assignedTo,
        String notes,
        String sourceLeadId,
        int followUpCount,
        int overdueFollowUpCount,
        Instant createdAt,
        Instant updatedAt) {
}
