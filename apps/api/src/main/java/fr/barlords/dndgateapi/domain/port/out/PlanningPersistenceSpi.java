package fr.barlords.dndgateapi.domain.port.out;

import fr.barlords.dndgateapi.domain.functional.model.planning.Planning;

import java.util.Optional;
import java.util.UUID;

public interface PlanningPersistenceSpi extends PersistenceSpi<Planning, UUID>{
    Optional<Planning> findByAdventureId(UUID adventureId);

}

