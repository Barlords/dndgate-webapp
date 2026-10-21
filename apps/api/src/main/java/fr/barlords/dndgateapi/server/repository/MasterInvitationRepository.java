package fr.barlords.dndgateapi.server.repository;

import fr.barlords.dndgateapi.server.entity.MasterInvitationEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Repository
@Transactional(propagation = Propagation.MANDATORY)
public interface MasterInvitationRepository extends JpaRepository<MasterInvitationEntity, UUID> {

    Optional<MasterInvitationEntity> findByCodeHash(String codeHash);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from MasterInvitationEntity i where i.adventureId = :adventureId")
    int deleteByAdventureId(@Param("adventureId") UUID adventureId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from MasterInvitationEntity i where i.id = :id")
    int deleteByInvitationId(@Param("id") UUID id);
}
