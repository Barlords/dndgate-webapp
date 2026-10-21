package fr.barlords.dndgateapi.client.dto.spellbook;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public record RemoveSpellToSpellBookRequest(
        @NotNull UUID spellbookId,
        @Size(min = 1, max = 255) String spellIndex
) {
}
