package fr.barlords.dndgateapi.domain.port.in.spellbook;

import java.util.UUID;

public interface SpellBookDeleterApi {

    /** Only the owner of the spell book can delete it, and only by retyping its name. */
    void deleteById(UUID accountId, UUID spellBookId, String confirmation);

}
