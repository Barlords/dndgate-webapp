package fr.barlords.dndgateapi.domain.functional.service.planning;

import fr.barlords.dndgateapi.domain.functional.model.planning.Availability;
import fr.barlords.dndgateapi.domain.port.in.planning.AvailabilityCreatorApi;
import fr.barlords.dndgateapi.domain.port.in.planning.AvailabilityUpdaterApi;
import lombok.RequiredArgsConstructor;

import java.util.UUID;

/**
 * An account has at most one vote per planning: creating a vote when one exists replaces its dates
 * (create or replace, so the call can be repeated safely).
 */
@RequiredArgsConstructor
public class AvailabilityCreatorService implements AvailabilityCreatorApi {

    private final AvailabilityUpdaterApi updater;

    @Override
    public Availability create(UUID accountId, Availability availability) {
        return updater.createOrUpdate(accountId, availability);
    }

}
