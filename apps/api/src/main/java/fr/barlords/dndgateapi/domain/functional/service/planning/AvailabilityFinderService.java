package fr.barlords.dndgateapi.domain.functional.service.planning;

import fr.barlords.dndgateapi.domain.functional.exception.planning.PlanningNotFoundException;
import fr.barlords.dndgateapi.domain.functional.model.planning.Availability;
import fr.barlords.dndgateapi.domain.functional.model.planning.Planning;
import fr.barlords.dndgateapi.domain.port.in.adventure.AdventureAccessVerifierApi;
import fr.barlords.dndgateapi.domain.port.in.planning.AvailabilityFinderApi;
import fr.barlords.dndgateapi.domain.port.out.AvailabilityPersistenceSpi;
import fr.barlords.dndgateapi.domain.port.out.PlanningPersistenceSpi;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.UUID;

@Slf4j
@RequiredArgsConstructor
public class AvailabilityFinderService implements AvailabilityFinderApi {

    private final AvailabilityPersistenceSpi spi;

    private final PlanningPersistenceSpi planningSpi;

    private final AdventureAccessVerifierApi adventureAccessVerifierApi;

    @Override
    public List<Availability> findByPlanningId(UUID accountId, UUID planningId) {
        Planning planning = planningSpi.findById(planningId)
                .orElseThrow(PlanningNotFoundException::new);

        adventureAccessVerifierApi.verifyParticipant(accountId, planning.getAdventureId());

        return spi.findByPlanningId(planningId);
    }

    @Override
    public List<Availability> findByAccountIdAndPlanningId(UUID accountId, UUID planningId) {
        return spi.findByAccountIdAndPlanningId(accountId, planningId);
    }

}
