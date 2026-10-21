package fr.barlords.dndgateapi.domain.port.in.planning;

import fr.barlords.dndgateapi.domain.functional.model.planning.Availability;

import java.util.UUID;

public interface AvailabilityUpdaterApi {
    Availability createOrUpdate(UUID accountId, Availability availability);

}
