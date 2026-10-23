package fr.barlords.dndgateapi.client.mapper;

import fr.barlords.dndgateapi.client.dto.magicitem.MagicItemCreationRequest;
import fr.barlords.dndgateapi.client.dto.magicitem.MagicItemDto;
import fr.barlords.dndgateapi.client.dto.magicitem.MagicItemPatchRequest;
import fr.barlords.dndgateapi.domain.functional.model.magicitem.MagicItem;

public interface MagicItemDtoMapper {

    static MagicItemDto toDto(MagicItem domain) {
        return new MagicItemDto(
                domain.getId(),
                domain.getName(),
                domain.getType(),
                domain.getRarity(),
                Boolean.TRUE.equals(domain.getRequiresAttunement()),
                domain.getDescription()
        );
    }

    static MagicItem creationRequestToDomain(MagicItemCreationRequest request) {
        return MagicItem.builder()
                .name(request.name().trim())
                .type(request.type())
                .rarity(request.rarity())
                .requiresAttunement(request.requiresAttunement())
                .description(request.description().trim())
                .build();
    }

    static MagicItem patchRequestToDomain(MagicItemPatchRequest request) {
        return MagicItem.builder()
                .name(Text.trimmed(request.name()))
                .type(request.type())
                .rarity(request.rarity())
                .requiresAttunement(request.requiresAttunement())
                .description(Text.trimmed(request.description()))
                .build();
    }

}
