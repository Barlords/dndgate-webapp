package fr.barlords.dndgateapi.client.mapper;

import fr.barlords.dndgateapi.client.dto.spellbook.SpellBookImportExportDto;
import fr.barlords.dndgateapi.domain.functional.model.SpellBook;

public interface SpellBookImportExportDtoMapper {

    static SpellBookImportExportDto toDto(SpellBook domain) {
        return new SpellBookImportExportDto(
                domain.getName(),
                domain.getDescription(),
                domain.getSpellList()
        );
    }

    static SpellBook importToDomain(SpellBookImportExportDto importDto) {
        return SpellBook.builder()
                .name(importDto.name().trim())
                .description(importDto.description().trim())
                .spellList(importDto.spellList())
                .build();
    }

}
