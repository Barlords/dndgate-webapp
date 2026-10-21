package fr.barlords.dndgateapi.server.repository;

import fr.barlords.dndgateapi.server.entity.AvailabilityEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Repository
@Transactional(propagation = Propagation.MANDATORY)
public interface AvailabilityRepository extends JpaRepository<AvailabilityEntity, UUID> {

    List<AvailabilityEntity> findByAccountIdAndPlanningId(UUID accountId, UUID planningId);

    List<AvailabilityEntity> findByPlanningId(UUID planningId);
    List<AvailabilityEntity> findAllByAccountId(UUID accountId);



}

