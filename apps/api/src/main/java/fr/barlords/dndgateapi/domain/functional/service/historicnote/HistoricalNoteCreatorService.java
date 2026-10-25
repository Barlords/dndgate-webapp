package fr.barlords.dndgateapi.domain.functional.service.historicnote;

import fr.barlords.dndgateapi.domain.functional.model.adventure.Adventure;
import fr.barlords.dndgateapi.domain.functional.model.adventure.note.HistoricalNote;
import fr.barlords.dndgateapi.domain.functional.service.adventure.AdventureOwnerVerifierService;
import fr.barlords.dndgateapi.domain.port.in.historicalnote.HistoricalNoteCreatorApi;
import fr.barlords.dndgateapi.domain.port.out.HistoricalNotePersistenceSpi;
import fr.barlords.dndgateapi.domain.functional.model.plan.PlanLimit;
import fr.barlords.dndgateapi.domain.port.in.plan.EntitlementApi;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.UUID;

@Slf4j
@RequiredArgsConstructor
public class HistoricalNoteCreatorService implements HistoricalNoteCreatorApi {

    private final HistoricalNotePersistenceSpi spi;

    private final AdventureOwnerVerifierService adventureOwnerVerifierService;

    private final EntitlementApi entitlementApi;

    @Override
    public HistoricalNote create(UUID accountId, UUID adventureId, HistoricalNote historicalNote) {
        Adventure adventure = adventureOwnerVerifierService.verify(accountId, adventureId);

        entitlementApi.verifyAdventureLimit(adventure, PlanLimit.HISTORICAL_NOTES_PER_ADVENTURE, spi.countByAdventureId(adventureId));

        return spi.save(historicalNote.withAdventure(adventure));
    }
}
