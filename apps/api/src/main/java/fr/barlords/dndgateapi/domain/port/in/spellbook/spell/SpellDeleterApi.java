package fr.barlords.dndgateapi.domain.port.in.spellbook.spell;

import java.util.UUID;

public interface SpellDeleterApi {
    void deleteSpell(UUID accountId, UUID spellBookId, String spellIndex);
}
