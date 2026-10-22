package fr.barlords.dndgateapi.bootstrap.config;

import fr.barlords.dndgateapi.domain.functional.exception.ConcurrentUpdateException;
import fr.barlords.dndgateapi.domain.port.in.spellbook.spell.SpellDeleterApi;
import lombok.RequiredArgsConstructor;

import java.util.UUID;

/** Same as {@link TransactionalSpellWriter} for the removal of a spell: two removals at the same moment both apply. */
@RequiredArgsConstructor
public class TransactionalSpellDeleter implements SpellDeleterApi {

    private final SpellDeleterApi delegate;

    private final RetryingTransaction transaction;

    @Override
    public void deleteSpell(UUID accountId, UUID spellBookId, String spellIndex) {
        transaction.execute(() -> {
            delegate.deleteSpell(accountId, spellBookId, spellIndex);
            return null;
        }, ConcurrentUpdateException.class);
    }
}
