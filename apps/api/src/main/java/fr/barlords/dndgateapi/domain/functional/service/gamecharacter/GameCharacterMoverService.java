package fr.barlords.dndgateapi.domain.functional.service.gamecharacter;

import fr.barlords.dndgateapi.domain.functional.exception.adventure.AdventureAccessDeniedException;
import fr.barlords.dndgateapi.domain.functional.exception.gamecharacter.GameCharacterPlacementException;
import fr.barlords.dndgateapi.domain.functional.model.GameCharacter;
import fr.barlords.dndgateapi.domain.functional.model.adventure.Adventure;
import fr.barlords.dndgateapi.domain.functional.model.plan.PlanLimit;
import fr.barlords.dndgateapi.domain.port.in.adventure.AdventureAccessVerifierApi;
import fr.barlords.dndgateapi.domain.port.in.gamecharacter.GameCharacterMoverApi;
import fr.barlords.dndgateapi.domain.port.in.plan.EntitlementApi;
import fr.barlords.dndgateapi.domain.port.out.GameCharacterPersistenceSpi;
import lombok.RequiredArgsConstructor;

import java.util.UUID;

@RequiredArgsConstructor
public class GameCharacterMoverService implements GameCharacterMoverApi {

    private final GameCharacterPersistenceSpi spi;

    private final GameCharacterOwnerVerifierService ownerVerifier;

    private final AdventureAccessVerifierApi adventureAccessVerifierApi;

    private final EntitlementApi entitlementApi;

    @Override
    public GameCharacter moveToReserve(UUID accountId, UUID characterId) {
        GameCharacter character = ownerVerifier.verify(accountId, characterId);
        if (!character.hasAdventure()) {
            return character;
        }

        entitlementApi.verifyAccountLimit(accountId, PlanLimit.CHARACTERS_WITHOUT_ADVENTURE, spi.countByOwnerIdWithoutAdventure(accountId));

        return spi.save(character.withAdventure(null).withActive(false));
    }

    @Override
    public GameCharacter moveToAdventure(UUID accountId, UUID characterId, UUID adventureId) {
        GameCharacter character = ownerVerifier.verify(accountId, characterId);
        if (character.hasAdventure()) {
            throw new GameCharacterPlacementException(GameCharacterPlacementException.NOT_IN_RESERVE);
        }

        Adventure adventure = adventureAccessVerifierApi.verifyParticipant(accountId, adventureId);
        // The master does not play a character of their own
        if (!adventure.getPlayers().contains(accountId)) {
            throw new AdventureAccessDeniedException();
        }

        entitlementApi.verifyAdventureLimit(adventure, PlanLimit.CHARACTERS_PER_PLAYER_AND_ADVENTURE,
                spi.countByOwnerIdAndAdventureId(accountId, adventureId));

        return spi.save(character.withAdventure(adventure).withActive(spi.findActive(accountId, adventureId).isEmpty()));
    }

    @Override
    public GameCharacter activate(UUID accountId, UUID adventureId, UUID characterId) {
        GameCharacter character = ownerVerifier.verify(accountId, characterId);
        if (!character.hasAdventure() || !adventureId.equals(character.getAdventure().getId())) {
            throw new GameCharacterPlacementException(GameCharacterPlacementException.NOT_IN_ADVENTURE);
        }
        if (character.isActive()) {
            return character;
        }

        // Deactivated first (and flushed) so that the unique index never sees two active characters
        spi.findActive(accountId, adventureId).ifPresent(previous -> spi.save(previous.withActive(false)));

        return spi.save(character.withActive(true));
    }

}
