package fr.barlords.dndgateapi.domain.functional.service.gamecharacter;

import java.util.Objects;

import fr.barlords.dndgateapi.domain.functional.model.GameCharacter;
import fr.barlords.dndgateapi.domain.port.in.gamecharacter.GameCharacterUpdaterApi;
import fr.barlords.dndgateapi.domain.port.out.GameCharacterPersistenceSpi;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.UUID;

@Slf4j
@RequiredArgsConstructor
public class GameCharacterUpdaterService implements GameCharacterUpdaterApi {

    private final GameCharacterPersistenceSpi spi;

    private final GameCharacterOwnerVerifierService gameCharacterOwnerVerifierService;

    @Override
    public GameCharacter update(UUID accountId, GameCharacter gameCharacter) {
        GameCharacter previousGameCharacter = gameCharacterOwnerVerifierService.verify(accountId, gameCharacter.getId());

        GameCharacter patchedGameCharacter = previousGameCharacter
                .withName(Objects.requireNonNullElse(gameCharacter.getName(), previousGameCharacter.getName()))
                .withRace(Objects.requireNonNullElse(gameCharacter.getRace(), previousGameCharacter.getRace()))
                .withMartialClass(Objects.requireNonNullElse(gameCharacter.getMartialClass(), previousGameCharacter.getMartialClass()))
                .withPersonality(Objects.requireNonNullElse(gameCharacter.getPersonality(), previousGameCharacter.getPersonality()))
                .withBackground(Objects.requireNonNullElse(gameCharacter.getBackground(), previousGameCharacter.getBackground()));

        return spi.save(patchedGameCharacter);
    }
}
