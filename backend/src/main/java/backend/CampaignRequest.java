package backend;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * Payload for creating and updating a marketing campaign.
 *
 * Bean Validation handles required values and lengths. The allowed
 * status/channel values and the start/end date ordering need business rules
 * and are enforced in {@link CampaignService}.
 */
public record CampaignRequest(

        @NotBlank(message = "Campaign name is required")
        @Size(max = 160, message = "Campaign name must be at most 160 characters")
        String name,

        @Size(max = 2000, message = "Description must be at most 2000 characters")
        String description,

        @Size(max = 30, message = "Channel must be at most 30 characters")
        String channel,

        @Size(max = 30, message = "Status must be at most 30 characters")
        String status,

        @NotNull(message = "Start date is required")
        LocalDate startDate,

        @NotNull(message = "End date is required")
        LocalDate endDate,

        @PositiveOrZero(message = "Budget cannot be negative")
        BigDecimal budget,

        @PositiveOrZero(message = "Spend cannot be negative")
        BigDecimal spend
) {

    public CampaignRequest {
        name = trim(name);
        description = trim(description);
        channel = trim(channel);
        status = trim(status);
    }

    private static String trim(String value) {
        return value == null ? null : value.trim();
    }
}
