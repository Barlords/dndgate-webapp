package fr.barlords.dndgateapi.server.mapper;

import fr.barlords.dndgateapi.domain.functional.model.magicitem.MagicItem;
import fr.barlords.dndgateapi.server.entity.MagicItemEntity;

public interface MagicItemEntityMapper {

    static MagicItem toDomain(MagicItemEntity entity) {
        return MagicItem.builder()
                .id(entity.getId())
                .owner(AccountEntityMapper.toDomain(entity.getOwner()))
                .name(entity.getName())
                .type(entity.getType())
                .rarity(entity.getRarity())
                .requiresAttunement(entity.isRequiresAttunement())
                .description(entity.getDescription())
                .version(entity.getVersion())
                .build();
    }

    static MagicItemEntity fromDomain(MagicItem domain) {
        return MagicItemEntity.builder()
                .id(domain.getId())
                .owner(AccountEntityMapper.fromDomain(domain.getOwner()))
                .name(domain.getName())
                .type(domain.getType())
                .rarity(domain.getRarity())
                .requiresAttunement(Boolean.TRUE.equals(domain.getRequiresAttunement()))
                .description(domain.getDescription())
                .version(domain.getVersion())
                .build();
    }

}
