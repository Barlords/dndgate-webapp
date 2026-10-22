package fr.barlords.dndgateapi.client.dto.message;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public record MessageCreationRequest(
        @NotNull UUID adventureId,
        @NotBlank @Size(min = 1, max = 5000) String content
) {
}
