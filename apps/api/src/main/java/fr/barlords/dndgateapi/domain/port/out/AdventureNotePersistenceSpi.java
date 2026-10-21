package fr.barlords.dndgateapi.domain.port.out;

import fr.barlords.dndgateapi.domain.functional.model.pagination.Page;
import fr.barlords.dndgateapi.domain.functional.model.pagination.PageRequest;

import fr.barlords.dndgateapi.domain.functional.model.adventure.note.AdventureNote;

import java.util.List;
import java.util.UUID;

public interface AdventureNotePersistenceSpi extends PersistenceSpi<AdventureNote, UUID> {

    /** Newest first. The private notes are only returned when asked for. */
    Page<AdventureNote> findByAdventureId(UUID adventureId, boolean includePrivate, PageRequest request);


    long countByAdventureId(UUID adventureId);
}
