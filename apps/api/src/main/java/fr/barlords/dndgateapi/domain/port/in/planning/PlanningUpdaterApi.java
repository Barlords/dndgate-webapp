package fr.barlords.dndgateapi.domain.port.in.planning;

import fr.barlords.dndgateapi.domain.functional.model.Upserted;
import fr.barlords.dndgateapi.domain.functional.model.planning.Planning;

import java.util.UUID;

public interface PlanningUpdaterApi {
    Upserted<Planning> createOrUpdate(UUID accountId, Planning planning);

}
