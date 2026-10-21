package fr.barlords.dndgateapi.client.dto.rating;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public record RatingPatchRequest(
        @NotNull @Min(1) @Max(5) int rate
) {
}
