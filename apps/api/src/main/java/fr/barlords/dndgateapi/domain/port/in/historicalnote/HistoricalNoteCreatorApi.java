package fr.barlords.dndgateapi.domain.port.in.historicalnote;

import fr.barlords.dndgateapi.domain.functional.model.adventure.note.HistoricalNote;

import java.util.UUID;

public interface HistoricalNoteCreatorApi {

    HistoricalNote create(UUID accountId, UUID adventureId, HistoricalNote historicalNote);

}
