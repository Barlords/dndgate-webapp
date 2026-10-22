package fr.barlords.dndgateapi.domain.functional.service.planning;

import fr.barlords.dndgateapi.domain.functional.exception.planning.PlanningAlreadyExistsException;
import fr.barlords.dndgateapi.domain.functional.model.planning.Planning;
import fr.barlords.dndgateapi.domain.functional.service.adventure.AdventureOwnerVerifierService;
import fr.barlords.dndgateapi.domain.port.in.planning.PlanningCreatorApi;
import fr.barlords.dndgateapi.domain.port.out.PlanningPersistenceSpi;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.UUID;

@Slf4j
@RequiredArgsConstructor
public class PlanningCreatorService implements PlanningCreatorApi {

    private final PlanningPersistenceSpi spi;

    private final AdventureOwnerVerifierService adventureOwnerVerifierService;

    @Override
    public Planning create(UUID accountId, Planning planning) {
        adventureOwnerVerifierService.verify(accountId, planning.getAdventureId());

        if (spi.findByAdventureId(planning.getAdventureId()).isPresent()) {
            throw new PlanningAlreadyExistsException();
        }

        return spi.save(planning);
    }

}
