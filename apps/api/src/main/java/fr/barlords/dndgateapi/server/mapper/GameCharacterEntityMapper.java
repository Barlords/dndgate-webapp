package fr.barlords.dndgateapi.server.mapper;

import fr.barlords.dndgateapi.domain.functional.model.GameCharacter;
import fr.barlords.dndgateapi.server.entity.GameCharacterEntity;

public interface GameCharacterEntityMapper {

    static GameCharacter toDomain(GameCharacterEntity entity) {
        return GameCharacter.builder()
                .id(entity.getId())
                .owner(AccountEntityMapper.toDomain(entity.getOwner()))
                .adventure(entity.getAdventure() == null ? null : AdventureEntityMapper.toDomain(entity.getAdventure()))
                .name(entity.getName())
                .race(entity.getRace())
                .martialClass(entity.getMartialClass())
                .personality(entity.getPersonality())
                .background(entity.getBackground())
                .picturePath(entity.getPicturePath())
                .active(entity.isActive())
                .version(entity.getVersion())
                .build();
    }

    static GameCharacterEntity fromDomain(GameCharacter domain) {
        return GameCharacterEntity.builder()
                .id(domain.getId())
                .owner(AccountEntityMapper.fromDomain(domain.getOwner()))
                .adventure(domain.getAdventure() == null ? null : AdventureEntityMapper.fromDomain(domain.getAdventure()))
                .name(domain.getName())
                .race(domain.getRace())
                .martialClass(domain.getMartialClass())
                .personality(domain.getPersonality())
                .background(domain.getBackground())
                .picturePath(domain.getPicturePath())
                .active(domain.isActive())
                .version(domain.getVersion())
                .build();
    }

}
