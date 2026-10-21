package fr.barlords.dndgateapi.domain.functional.service.planning;

import fr.barlords.dndgateapi.domain.functional.exception.adventure.AdventureNotFoundException;
import fr.barlords.dndgateapi.domain.functional.exception.planning.PlanningNotFoundException;
import fr.barlords.dndgateapi.domain.functional.model.planning.Planning;
import fr.barlords.dndgateapi.domain.port.in.adventure.AdventureFinderApi;
import fr.barlords.dndgateapi.domain.port.out.PlanningPersistenceSpi;
import lombok.RequiredArgsConstructor;

import java.util.UUID;

/**
 * Checks that a planning exists and that its adventure is visible to the account
 * (public adventure or participant): candidates can vote before joining.
 */
@RequiredArgsConstructor
public class PlanningVisibilityVerifierService {

    private final PlanningPersistenceSpi planningSpi;

    private final AdventureFinderApi adventureFinderApi;

    public Planning verify(UUID accountId, UUID planningId) {
        Planning planning = planningSpi.findById(planningId)
                .orElseThrow(PlanningNotFoundException::new);

        adventureFinderApi.findByIdVisibleTo(accountId, planning.getAdventureId())
                .orElseThrow(AdventureNotFoundException::new);

        return planning;
    }

}
