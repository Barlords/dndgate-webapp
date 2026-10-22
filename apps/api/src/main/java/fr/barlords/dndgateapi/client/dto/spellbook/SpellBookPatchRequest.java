package fr.barlords.dndgateapi.client.dto.spellbook;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import fr.barlords.dndgateapi.client.validator.Patterns;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public record SpellBookPatchRequest(
        @Pattern(regexp = Patterns.NOT_BLANK) @Size(max = 255) String name,
        @Pattern(regexp = Patterns.NOT_BLANK) @Size(max = 5000) String description
) {
}
