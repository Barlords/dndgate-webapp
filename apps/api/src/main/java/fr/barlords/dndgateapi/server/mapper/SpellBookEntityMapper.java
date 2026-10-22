package fr.barlords.dndgateapi.server.mapper;

import java.util.HashSet;

import fr.barlords.dndgateapi.domain.functional.model.SpellBook;
import fr.barlords.dndgateapi.server.entity.SpellBookEntity;

public interface SpellBookEntityMapper {

    static SpellBook toDomain(SpellBookEntity entity) {
        return SpellBook.builder()
                .id(entity.getId())
                .owner(AccountEntityMapper.toDomain(entity.getOwner()))
                .name(entity.getName())
                .description(entity.getDescription())
                .spellList(new HashSet<>(entity.getSpellList()))
                .version(entity.getVersion())
                .build();
    }

    static SpellBookEntity fromDomain(SpellBook domain) {
        return SpellBookEntity.builder()
                .id(domain.getId())
                .owner(AccountEntityMapper.fromDomain(domain.getOwner()))
                .name(domain.getName())
                .description(domain.getDescription())
                .spellList(new HashSet<>(domain.getSpellList()))
                .version(domain.getVersion())
                .build();
    }

}
