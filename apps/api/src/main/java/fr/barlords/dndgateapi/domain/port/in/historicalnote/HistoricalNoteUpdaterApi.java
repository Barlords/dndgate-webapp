package fr.barlords.dndgateapi.domain.port.in.historicalnote;

import fr.barlords.dndgateapi.domain.functional.model.adventure.note.HistoricalNote;

import java.util.UUID;

public interface HistoricalNoteUpdaterApi {

    HistoricalNote update(UUID accountId, HistoricalNote historicalNote);

}
