package fr.barlords.dndgateapi.bootstrap.config;

import fr.barlords.dndgateapi.domain.functional.model.GameCharacter;
import fr.barlords.dndgateapi.domain.port.in.gamecharacter.GameCharacterMoverApi;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Switching the active character deactivates one and activates another: either both happen or neither does.
 */
@RequiredArgsConstructor
public class TransactionalGameCharacterMover implements GameCharacterMoverApi {

    private final GameCharacterMoverApi delegate;

    @Override
    @Transactional
    public GameCharacter moveToReserve(UUID accountId, UUID characterId) {
        return delegate.moveToReserve(accountId, characterId);
    }

    @Override
    @Transactional
    public GameCharacter moveToAdventure(UUID accountId, UUID characterId, UUID adventureId) {
        return delegate.moveToAdventure(accountId, characterId, adventureId);
    }

    @Override
    @Transactional
    public GameCharacter activate(UUID accountId, UUID adventureId, UUID characterId) {
        return delegate.activate(accountId, adventureId, characterId);
    }

}
