package fr.barlords.dndgateapi.client.dto.gamecharacter;

import fr.barlords.dndgateapi.client.validator.Patterns;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public record GameCharacterPatchRequest(
        @Pattern(regexp = Patterns.NOT_BLANK) @Size(max = 50) String name,
        @Pattern(regexp = Patterns.NOT_BLANK) @Size(max = 255) String race,
        @Pattern(regexp = Patterns.NOT_BLANK) @Size(max = 255) String martialClass,
        @Pattern(regexp = Patterns.NOT_BLANK) @Size(max = 5000) String personality,
        @Pattern(regexp = Patterns.NOT_BLANK) @Size(max = 80000) String background
) {
}
