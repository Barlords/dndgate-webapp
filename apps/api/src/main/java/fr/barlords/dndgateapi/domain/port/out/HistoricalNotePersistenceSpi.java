package fr.barlords.dndgateapi.domain.port.out;

import fr.barlords.dndgateapi.domain.functional.model.pagination.Page;
import fr.barlords.dndgateapi.domain.functional.model.pagination.PageRequest;

import fr.barlords.dndgateapi.domain.functional.model.adventure.note.HistoricalNote;

import java.util.List;
import java.util.UUID;

public interface HistoricalNotePersistenceSpi extends PersistenceSpi<HistoricalNote, UUID> {

    /** Newest first. */
    Page<HistoricalNote> findByAdventureId(UUID adventureId, PageRequest request);


    long countByAdventureId(UUID adventureId);
}
