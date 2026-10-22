package fr.barlords.dndgateapi.client.dto.rating;

import com.fasterxml.jackson.annotation.JsonAutoDetect;

import java.time.Instant;
import java.util.UUID;

@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public record RatingDto(
        UUID id,
        UUID emitterId,
        UUID targetId,
        int rate,
        Instant createdAt
) {

}