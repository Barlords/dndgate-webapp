package fr.barlords.dndgateapi.client.dto.rating;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * The public average of the ratings an account received. {@code average} is null when nobody rated the account.
 */
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
@JsonInclude(JsonInclude.Include.ALWAYS)
public record RatingMeanDto(
        @Schema(nullable = true) Double average,
        long count
) {
}
