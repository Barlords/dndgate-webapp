package fr.barlords.dndgateapi.client.dto.gamecharacter;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public record GameCharacterCreationRequest(
        /** Absent for a character kept outside any adventure of the application. */
        UUID adventureId,
        @NotBlank @Size(min = 3, max = 50) String name,
        @NotBlank @Size(min = 3, max = 255) String race,
        @NotBlank @Size(min = 1, max = 255) String martialClass,
        @NotBlank @Size(min = 1, max = 5000) String personality,
        @NotBlank @Size(min = 1, max = 80000) String background
) {
}
