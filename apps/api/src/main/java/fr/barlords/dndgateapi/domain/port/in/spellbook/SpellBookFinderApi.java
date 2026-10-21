package fr.barlords.dndgateapi.domain.port.in.spellbook;

import fr.barlords.dndgateapi.domain.functional.model.SpellBook;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SpellBookFinderApi {

    /**
     * A spell book is personal: only its owner can read it.
     */
    Optional<SpellBook> findById(UUID accountId, UUID id);

    /**
     * A caller can only list its own spell books.
     */
    List<SpellBook> findByOwnerId(UUID accountId, UUID ownerId);

    List<SpellBook> findMyBook(UUID accountId);

}
