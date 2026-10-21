package fr.barlords.dndgateapi.client.dto.spellbook;

import com.fasterxml.jackson.annotation.JsonAutoDetect;

import java.util.Set;

@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public record SpellBookImportExportDto (
    String name,
    String description,
    Set<String> spellList
) {}
