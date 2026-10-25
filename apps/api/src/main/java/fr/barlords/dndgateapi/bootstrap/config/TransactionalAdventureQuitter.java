package fr.barlords.dndgateapi.bootstrap.config;

import fr.barlords.dndgateapi.domain.functional.model.adventure.Adventure;
import fr.barlords.dndgateapi.domain.functional.model.adventure.AdventureStatus;
import fr.barlords.dndgateapi.domain.functional.model.adventure.AdventureVisibility;
import fr.barlords.dndgateapi.domain.port.in.adventure.AdventureUpdaterApi;
import lombok.RequiredArgsConstructor;

import java.util.Map;
import java.util.UUID;

/**
 * Leaving an adventure moves or deletes characters and frees a seat: either everything is written or nothing is.
 * Each attempt runs in its own transaction and is replayed when another player changed the adventure meanwhile.
 */
@RequiredArgsConstructor
public class TransactionalAdventureQuitter implements AdventureUpdaterApi {

    private final AdventureUpdaterApi delegate;

    private final RetryingTransaction transaction;

    @Override
    public Adventure quitAdventure(UUID adventureId, UUID accountId, Map<UUID, String> deletions) {
        return transaction.execute(() -> delegate.quitAdventure(adventureId, accountId, deletions));
    }

    @Override
    public Adventure update(UUID accountId, Adventure adventure) {
        return delegate.update(accountId, adventure);
    }

    @Override
    public Adventure updateVisibility(UUID accountId, UUID id, AdventureVisibility visibility) {
        return delegate.updateVisibility(accountId, id, visibility);
    }

    @Override
    public Adventure updateStatus(UUID accountId, UUID id, AdventureStatus status) {
        return delegate.updateStatus(accountId, id, status);
    }

    @Override
    public Adventure joinAdventure(UUID adventureId, UUID accountId) {
        return delegate.joinAdventure(adventureId, accountId);
    }

    @Override
    public Adventure joinAdventureByCode(UUID accountId, String code) {
        return delegate.joinAdventureByCode(accountId, code);
    }

    @Override
    public void sendInvitationCode(UUID accountId, UUID adventureId, String targetPseudo) {
        delegate.sendInvitationCode(accountId, adventureId, targetPseudo);
    }
}
