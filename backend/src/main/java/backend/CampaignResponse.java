package backend;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Campaign representation returned by the API.
 *
 * Performance fields are derived at read time from persisted leads whose
 * {@code campaignId} points at this campaign. Nothing is stored twice.
 */
public record CampaignResponse(
        String id,
        String name,
        String description,
        String channel,
        String status,
        LocalDate startDate,
        LocalDate endDate,
        BigDecimal budget,
        BigDecimal spend,
        boolean activeNow,
        int leadCount,
        int wonLeadCount,
        int convertedLeadCount,
        double conversionRate,
        BigDecimal pipelineValue,
        BigDecimal wonValue,
        Instant createdAt,
        Instant updatedAt) {
}
