package fr.barlords.dndgateapi.server.repository;

import fr.barlords.dndgateapi.server.entity.GameCharacterEntity;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@Transactional(propagation = Propagation.MANDATORY)
public interface GameCharacterRepository extends JpaRepository<GameCharacterEntity, UUID> {

    // The mapper reads the adventure of every character: fetched with them, not once per character (N+1)
    @EntityGraph(attributePaths = "adventure")
    List<GameCharacterEntity> findByOwner_Id(UUID owner_id);

    @EntityGraph(attributePaths = "adventure")
    List<GameCharacterEntity> findByAdventure_Id(UUID adventure_id);


    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from GameCharacterEntity e where e.adventure.id = :adventureId")
    void deleteByAdventureId(@Param("adventureId") UUID adventureId);


    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update GameCharacterEntity e set e.adventure = null, e.active = false where e.adventure.id = :adventureId")
    void moveToReserveByAdventureId(@Param("adventureId") UUID adventureId);

    long countByOwner_IdAndAdventure_Id(UUID ownerId, UUID adventureId);

    @EntityGraph(attributePaths = "adventure")
    Optional<GameCharacterEntity> findByOwner_IdAndAdventure_IdAndActiveTrue(UUID ownerId, UUID adventureId);

    long countByOwner_IdAndAdventureIsNull(UUID ownerId);
}
