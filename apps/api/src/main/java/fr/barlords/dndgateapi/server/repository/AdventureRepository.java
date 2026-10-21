package fr.barlords.dndgateapi.server.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import fr.barlords.dndgateapi.server.entity.AdventureEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@Transactional(propagation = Propagation.MANDATORY)
public interface AdventureRepository extends JpaRepository<AdventureEntity, UUID> {

    List<AdventureEntity> findAllByCreatorId(UUID creatorId);

    @Query("SELECT g FROM AdventureEntity g JOIN g.players p WHERE p = :playerId")
    List<AdventureEntity> findAllByPlayersId(@Param("playerId") UUID accountId);

    @Query("SELECT a FROM AdventureEntity a WHERE a.visibility = fr.barlords.dndgateapi.domain.functional.model.adventure.AdventureVisibility.PUBLIC OR a.creatorId = :accountId OR :accountId MEMBER OF a.players")
    Page<AdventureEntity> findVisibleTo(@Param("accountId") UUID accountId, Pageable pageable);

    Page<AdventureEntity> findByCreatorId(UUID creatorId, Pageable pageable);

    @Query("SELECT a FROM AdventureEntity a WHERE :playerId MEMBER OF a.players")
    Page<AdventureEntity> findPageByPlayerId(@Param("playerId") UUID playerId, Pageable pageable);

    @Query("SELECT a FROM AdventureEntity a WHERE a.visibility = fr.barlords.dndgateapi.domain.functional.model.adventure.AdventureVisibility.PUBLIC AND a.status = fr.barlords.dndgateapi.domain.functional.model.adventure.AdventureStatus.NOT_STARTED "
            + "AND a.creatorId <> :accountId AND :accountId NOT MEMBER OF a.players")
    Page<AdventureEntity> findAvailableTo(@Param("accountId") UUID accountId, Pageable pageable);

    Optional<AdventureEntity> findByInvitationCode(String invitationCode);


    /** Adventures the account runs that are not over: the ones that count against its limit. */
    @Query("SELECT COUNT(a) FROM AdventureEntity a WHERE a.creatorId = :ownerId AND a.masterVacantSince IS NULL "
            + "AND a.status IN (fr.barlords.dndgateapi.domain.functional.model.adventure.AdventureStatus.NOT_STARTED, "
            + "fr.barlords.dndgateapi.domain.functional.model.adventure.AdventureStatus.ONGOING)")
    long countActiveByCreatorId(@Param("ownerId") UUID ownerId);
}
