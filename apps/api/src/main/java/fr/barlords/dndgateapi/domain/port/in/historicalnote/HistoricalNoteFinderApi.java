package fr.barlords.dndgateapi.domain.port.in.historicalnote;

import fr.barlords.dndgateapi.domain.functional.model.pagination.Page;
import fr.barlords.dndgateapi.domain.functional.model.pagination.PageRequest;

import fr.barlords.dndgateapi.domain.functional.model.adventure.note.HistoricalNote;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface HistoricalNoteFinderApi {

    /**
     * Participants only.
     */
    Optional<HistoricalNote> findById(UUID accountId, UUID id);

    /**
     * Participants only.
     */
    Page<HistoricalNote> findByAdventureId(UUID accountId, UUID adventureId, PageRequest request);

}
