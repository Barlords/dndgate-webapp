package fr.barlords.dndgateapi.client.mapper;

import fr.barlords.dndgateapi.client.dto.spellbook.SpellBookCreationRequest;
import fr.barlords.dndgateapi.client.dto.spellbook.SpellBookDto;
import fr.barlords.dndgateapi.client.dto.spellbook.SpellBookPatchRequest;
import fr.barlords.dndgateapi.domain.functional.model.SpellBook;


public interface SpellBookDtoMapper {

    static SpellBookDto toDto(SpellBook domain) {
        return new SpellBookDto(
                domain.getId(),
                domain.getName(),
                domain.getDescription(),
                domain.getSpellList()
        );
    }

    static SpellBook creationRequestToDomain(SpellBookCreationRequest request) {
        return SpellBook.builder()
                .name(request.name().trim())
                .description(request.description().trim())
                .build();
    }

    static SpellBook patchRequestToDomain(SpellBookPatchRequest request) {
        return SpellBook.builder()
                .name(request.name().trim())
                .description(request.description().trim())
                .build();
    }
}
