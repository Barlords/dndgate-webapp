package fr.barlords.dndgateapi.domain.port.in.planning;

import fr.barlords.dndgateapi.domain.functional.model.planning.Availability;

import java.util.List;
import java.util.UUID;

public interface AvailabilityFinderApi {

    /**
     * Every vote of a planning: participants of the adventure only.
     */
    List<Availability> findByPlanningId(UUID accountId, UUID planningId);

    /**
     * The caller own votes for a planning.
     */
    List<Availability> findByAccountIdAndPlanningId(UUID accountId, UUID planningId);

}
