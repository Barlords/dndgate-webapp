package fr.barlords.dndgateapi.domain.port.in.spellbook.spell;

import fr.barlords.dndgateapi.domain.functional.model.SpellBook;

import java.util.UUID;

public interface SpellWriterApi {
    SpellBook addSpell(UUID accountId, UUID spellBookId, String spellIndex);
}
