package backend;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Payload for changing a campaign's lifecycle status. */
public record CampaignStatusRequest(

        @NotBlank(message = "Status is required")
        @Size(max = 30, message = "Status must be at most 30 characters")
        String status
) {
}
