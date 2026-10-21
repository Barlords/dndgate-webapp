package fr.barlords.dndgateapi.server.repository;

import fr.barlords.dndgateapi.server.entity.PlanningEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Repository
@Transactional(propagation = Propagation.MANDATORY)
public interface PlanningRepository extends JpaRepository<PlanningEntity, UUID> {

    @Query("SELECT p FROM PlanningEntity p WHERE p.adventureId = :adventureId")
    List<PlanningEntity> findByAdventureId(@Param("adventureId") UUID adventureId);
}
