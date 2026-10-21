package fr.barlords.dndgateapi.client.dto.spellbook;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import jakarta.validation.constraints.Size;

@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public record AddSpellToSpellBookRequest(
        @Size(min = 1, max = 255) String spellIndex
) {
}
