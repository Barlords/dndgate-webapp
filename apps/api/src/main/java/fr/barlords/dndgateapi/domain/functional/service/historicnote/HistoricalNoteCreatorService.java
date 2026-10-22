package fr.barlords.dndgateapi.domain.functional.service.historicnote;

import fr.barlords.dndgateapi.domain.functional.exception.QuantityLimitExceededException;
import fr.barlords.dndgateapi.domain.functional.model.adventure.Adventure;
import fr.barlords.dndgateapi.domain.functional.model.adventure.note.HistoricalNote;
import fr.barlords.dndgateapi.domain.functional.service.adventure.AdventureOwnerVerifierService;
import fr.barlords.dndgateapi.domain.port.in.historicalnote.HistoricalNoteCreatorApi;
import fr.barlords.dndgateapi.domain.port.out.HistoricalNotePersistenceSpi;
import fr.barlords.dndgateapi.domain.functional.model.QuantityLimits;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.UUID;

@Slf4j
@RequiredArgsConstructor
public class HistoricalNoteCreatorService implements HistoricalNoteCreatorApi {

    private final HistoricalNotePersistenceSpi spi;

    private final AdventureOwnerVerifierService adventureOwnerVerifierService;

    private final QuantityLimits limits;

    @Override
    public HistoricalNote create(UUID accountId, UUID adventureId, HistoricalNote historicalNote) {
        Adventure adventure = adventureOwnerVerifierService.verify(accountId, adventureId);

        if (spi.countByAdventureId(adventureId) >= limits.historicalNotesPerAdventure()) {
            throw QuantityLimitExceededException.historicalNotes(limits.historicalNotesPerAdventure());
        }

        return spi.save(historicalNote.withAdventure(adventure));
    }
}
