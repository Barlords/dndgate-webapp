package fr.barlords.dndgateapi.domain.functional.service.adventurenote;

import fr.barlords.dndgateapi.domain.functional.model.adventure.Adventure;
import fr.barlords.dndgateapi.domain.functional.model.adventure.note.AdventureNote;
import fr.barlords.dndgateapi.domain.functional.service.adventure.AdventureOwnerVerifierService;
import fr.barlords.dndgateapi.domain.port.in.adventurenote.AdventureNoteCreatorApi;
import fr.barlords.dndgateapi.domain.port.out.AdventureNotePersistenceSpi;
import fr.barlords.dndgateapi.domain.functional.model.plan.PlanLimit;
import fr.barlords.dndgateapi.domain.port.in.plan.EntitlementApi;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.UUID;

@Slf4j
@RequiredArgsConstructor
public class AdventureNoteCreatorService implements AdventureNoteCreatorApi {

    private final AdventureNotePersistenceSpi spi;

    private final AdventureOwnerVerifierService adventureOwnerVerifierService;

    private final EntitlementApi entitlementApi;

    @Override
    public AdventureNote create(UUID accountId, UUID adventureId, AdventureNote adventureNote) {
        Adventure adventure = adventureOwnerVerifierService.verify(accountId, adventureId);

        entitlementApi.verifyAdventureLimit(adventure, PlanLimit.MASTER_NOTES_PER_ADVENTURE, spi.countByAdventureId(adventureId));

        return spi.save(adventureNote.withAdventure(adventure));
    }
}
