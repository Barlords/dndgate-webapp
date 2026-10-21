package fr.barlords.dndgateapi.client.dto.message;

import fr.barlords.dndgateapi.client.validator.ValidDate;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public record MessageCreationRequest(
        @NotNull String adventureId,
        @NotBlank @Size(min = 1, max = 5000) String content,
        @NotNull @ValidDate LocalDateTime sendDate
) {
}
