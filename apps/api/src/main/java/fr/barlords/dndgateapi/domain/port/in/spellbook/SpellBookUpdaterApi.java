package fr.barlords.dndgateapi.domain.port.in.spellbook;

import fr.barlords.dndgateapi.domain.functional.model.SpellBook;

import java.util.UUID;

public interface SpellBookUpdaterApi {

    SpellBook update(UUID accountId, SpellBook spellBook);

}
