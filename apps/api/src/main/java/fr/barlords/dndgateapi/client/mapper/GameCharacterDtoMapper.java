package fr.barlords.dndgateapi.client.mapper;

import fr.barlords.dndgateapi.client.dto.gamecharacter.GameCharacterCreationRequest;
import fr.barlords.dndgateapi.client.dto.gamecharacter.GameCharacterDto;
import fr.barlords.dndgateapi.client.dto.gamecharacter.GameCharacterPatchRequest;
import fr.barlords.dndgateapi.domain.functional.model.GameCharacter;


public interface GameCharacterDtoMapper {

    static GameCharacterDto toDto(GameCharacter domain) {
        return new GameCharacterDto(
                domain.getId(),
                domain.getName(),
                domain.getRace(),
                domain.getMartialClass(),
                domain.getPersonality(),
                domain.getBackground(),
                domain.getPicturePath() == null ? null : "/game-characters/" + domain.getId() + "/picture",
                domain.getOwner().getId(),
                domain.hasAdventure() ? domain.getAdventure().getId() : null,
                domain.isActive()
        );
    }

    static GameCharacter creationRequestToDomain(GameCharacterCreationRequest request) {
        return GameCharacter.builder()
                .name(request.name().trim())
                .race(request.race().trim())
                .martialClass(request.martialClass().trim())
                .personality(request.personality().trim())
                .background(request.background().trim())
                .build();
    }

    static GameCharacter patchRequestToDomain(GameCharacterPatchRequest request) {
        return GameCharacter.builder()
                .name(Text.trimmed(request.name()))
                .race(Text.trimmed(request.race()))
                .martialClass(Text.trimmed(request.martialClass()))
                .personality(Text.trimmed(request.personality()))
                .background(Text.trimmed(request.background()))
                .build();
    }

}
