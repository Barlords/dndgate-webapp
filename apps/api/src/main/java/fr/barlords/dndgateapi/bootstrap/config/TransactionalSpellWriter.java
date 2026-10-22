package fr.barlords.dndgateapi.bootstrap.config;

import fr.barlords.dndgateapi.domain.functional.exception.ConcurrentUpdateException;
import fr.barlords.dndgateapi.domain.functional.model.SpellBook;
import fr.barlords.dndgateapi.domain.port.in.spellbook.spell.SpellWriterApi;
import lombok.RequiredArgsConstructor;

import java.util.UUID;

/**
 * Adding a spell is "read the book, then save it with one more spell". When the book changed in between (another
 * spell added at the same moment), the save is refused: the operation is replayed from a fresh read, so that both
 * spells are kept.
 */
@RequiredArgsConstructor
public class TransactionalSpellWriter implements SpellWriterApi {

    private final SpellWriterApi delegate;

    private final RetryingTransaction transaction;

    @Override
    public SpellBook addSpell(UUID accountId, UUID spellBookId, String spellIndex) {
        return transaction.execute(() -> delegate.addSpell(accountId, spellBookId, spellIndex), ConcurrentUpdateException.class);
    }
}
