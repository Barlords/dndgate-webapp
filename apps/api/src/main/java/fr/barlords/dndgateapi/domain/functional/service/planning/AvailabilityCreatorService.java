package fr.barlords.dndgateapi.domain.functional.service.planning;

import fr.barlords.dndgateapi.domain.functional.exception.DataNotSaveException;
import fr.barlords.dndgateapi.domain.functional.model.planning.Availability;
import fr.barlords.dndgateapi.domain.port.in.planning.AvailabilityCreatorApi;
import fr.barlords.dndgateapi.domain.port.out.AvailabilityPersistenceSpi;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.UUID;

@Slf4j
@RequiredArgsConstructor
public class AvailabilityCreatorService implements AvailabilityCreatorApi {

    private final AvailabilityPersistenceSpi spi;

    private final PlanningVisibilityVerifierService planningVisibilityVerifierService;

    @Override
    public Availability create(UUID accountId, Availability availability) {
        planningVisibilityVerifierService.verify(accountId, availability.getPlanningId());

        return spi.save(availability.withAccountId(accountId)).getOrElseThrow(DataNotSaveException::new);
    }

}
