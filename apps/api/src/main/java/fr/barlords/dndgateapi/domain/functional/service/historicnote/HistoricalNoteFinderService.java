package fr.barlords.dndgateapi.domain.functional.service.historicnote;

import fr.barlords.dndgateapi.domain.functional.model.pagination.Page;
import fr.barlords.dndgateapi.domain.functional.model.pagination.PageRequest;

import fr.barlords.dndgateapi.domain.functional.model.adventure.note.HistoricalNote;
import fr.barlords.dndgateapi.domain.port.in.adventure.AdventureAccessVerifierApi;
import fr.barlords.dndgateapi.domain.port.in.historicalnote.HistoricalNoteFinderApi;
import fr.barlords.dndgateapi.domain.port.out.HistoricalNotePersistenceSpi;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@RequiredArgsConstructor
public class HistoricalNoteFinderService implements HistoricalNoteFinderApi {

    private final HistoricalNotePersistenceSpi spi;

    private final AdventureAccessVerifierApi adventureAccessVerifierApi;

    @Override
    public Optional<HistoricalNote> findById(UUID accountId, UUID id) {
        return spi.findById(id).map(note -> {
            adventureAccessVerifierApi.verifyParticipant(accountId, note.getAdventure().getId());
            return note;
        });
    }

    @Override
    public Page<HistoricalNote> findByAdventureId(UUID accountId, UUID adventureId, PageRequest request) {
        adventureAccessVerifierApi.verifyParticipant(accountId, adventureId);

        return spi.findByAdventureId(adventureId, request);
    }

}
