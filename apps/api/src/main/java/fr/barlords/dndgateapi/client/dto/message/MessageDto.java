package fr.barlords.dndgateapi.client.dto.message;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;
import java.util.UUID;

@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public record MessageDto(
        @NotNull UUID id,
        @NotNull UUID adventureId,
        @NotNull UUID senderId,
        @NotNull String content,
        @NotNull LocalDateTime sendDate
) {
}
