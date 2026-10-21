package fr.barlords.dndgateapi.domain.port.out;

import fr.barlords.dndgateapi.domain.ApplicationError;
import fr.barlords.dndgateapi.domain.functional.model.planning.Availability;
import io.vavr.control.Either;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
public interface AvailabilityPersistenceSpi extends PersistenceSpi<Availability, UUID> {
    Optional<Availability> findById(UUID id);
    List<Availability> findByPlanningId(UUID planningId);

    void deleteByPlanningId(UUID planningId);
    List<Availability> findByAccountIdAndPlanningId(UUID accountId, UUID planningId);
    Either<ApplicationError, Availability> save(Availability availability);
}