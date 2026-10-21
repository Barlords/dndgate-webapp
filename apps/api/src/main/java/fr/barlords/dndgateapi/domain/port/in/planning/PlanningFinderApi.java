package fr.barlords.dndgateapi.domain.port.in.planning;

import fr.barlords.dndgateapi.domain.functional.model.planning.Planning;

import java.util.Optional;
import java.util.UUID;

public interface PlanningFinderApi {

    /**
     * Readable as long as the adventure is visible to the caller
     * (public adventure, or caller is a participant), so that candidates can vote before joining.
     */
    Optional<Planning> findById(UUID accountId, UUID id);

    Optional<Planning> findByAdventureId(UUID accountId, UUID adventureId);

}
