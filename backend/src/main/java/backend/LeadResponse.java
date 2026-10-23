package backend;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Lead representation returned by the API.
 *
 * Includes the stable assigned-employee id (for edit forms) plus a resolved
 * {@link EmployeeRef}, the conversion outcome (client id/name/timestamp) and
 * live follow-up counts.
 */
public record LeadResponse(
        String id,
        String name,
        String company,
        String email,
        String phone,
        String source,
        String status,
        String priority,
        String assignedToId,
        EmployeeRef assignedTo,
        String campaignId,
        String campaignName,
        BigDecimal estimatedValue,
        LocalDate followUpDate,
        String notes,
        String convertedClientId,
        String convertedClientName,
        Instant convertedAt,
        int followUpCount,
        int overdueFollowUpCount,
        Instant createdAt,
        Instant updatedAt) {
}
