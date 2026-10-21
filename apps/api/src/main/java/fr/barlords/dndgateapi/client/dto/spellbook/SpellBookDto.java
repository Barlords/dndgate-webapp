package fr.barlords.dndgateapi.client.dto.spellbook;

import com.fasterxml.jackson.annotation.JsonAutoDetect;

import java.util.Set;
import java.util.UUID;

@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public record SpellBookDto(
        UUID id,
        String name,
        String description,
        Set<String> spellList
) {
}
