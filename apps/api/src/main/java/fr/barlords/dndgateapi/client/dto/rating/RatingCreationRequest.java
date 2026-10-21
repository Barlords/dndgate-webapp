package fr.barlords.dndgateapi.client.dto.rating;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public record RatingCreationRequest(
        @NotNull UUID accountTargetedId,
        @NotNull @Min(1) @Max(5) int rate
) {
}
