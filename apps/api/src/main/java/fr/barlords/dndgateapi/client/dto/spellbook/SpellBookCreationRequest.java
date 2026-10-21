package fr.barlords.dndgateapi.client.dto.spellbook;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public record SpellBookCreationRequest(
        @NotBlank @Size(min = 1, max = 255) String name,
        @NotBlank @Size(min = 1, max = 5000) String description
) {
}
