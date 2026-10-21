package fr.barlords.dndgateapi.domain.functional.service.planning;

import fr.barlords.dndgateapi.domain.functional.exception.DataNotSaveException;
import fr.barlords.dndgateapi.domain.functional.model.planning.Availability;
import fr.barlords.dndgateapi.domain.port.in.planning.AvailabilityUpdaterApi;
import fr.barlords.dndgateapi.domain.port.out.AvailabilityPersistenceSpi;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.UUID;

@Slf4j
@RequiredArgsConstructor
public class AvailabilityUpdaterService implements AvailabilityUpdaterApi {

    private final AvailabilityPersistenceSpi spi;

    private final PlanningVisibilityVerifierService planningVisibilityVerifierService;

    @Override
    public Availability createOrUpdate(UUID accountId, Availability availability) {
        planningVisibilityVerifierService.verify(accountId, availability.getPlanningId());

        // A caller can only vote for itself, whatever the account id sent by the client
        Availability own = availability.withAccountId(accountId);

        List<Availability> existing = spi.findByAccountIdAndPlanningId(accountId, own.getPlanningId());

        if (existing.isEmpty()) {
            return spi.save(own).getOrElseThrow(DataNotSaveException::new);
        }

        // At most one vote exists per (planning, account): it keeps its id and gets the new dates
        return spi.save(existing.get(0).withAvailabilityDates(own.getAvailabilityDates()))
                .getOrElseThrow(DataNotSaveException::new);
    }

}
