package fr.barlords.dndgateapi.domain.port.out;

import fr.barlords.dndgateapi.domain.functional.model.SpellBook;

import java.util.List;
import java.util.UUID;

public interface SpellBookPersistenceSpi extends PersistenceSpi<SpellBook, UUID> {

    List<SpellBook> findByOwnerId(UUID ownerId);
}
