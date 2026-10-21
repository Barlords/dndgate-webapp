package fr.barlords.dndgateapi.server.repository;

import fr.barlords.dndgateapi.domain.functional.model.adventure.master.MasterClaimStatus;
import fr.barlords.dndgateapi.server.entity.MasterClaimEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Repository
@Transactional(propagation = Propagation.MANDATORY)
public interface MasterClaimRepository extends JpaRepository<MasterClaimEntity, UUID> {

    Optional<MasterClaimEntity> findByAdventureIdAndStatus(UUID adventureId, MasterClaimStatus status);
}
