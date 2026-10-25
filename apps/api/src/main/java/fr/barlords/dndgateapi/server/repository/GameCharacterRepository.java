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
    // The non-player characters of a game master are not "my characters": they never count in a player's lists or quotas
    @EntityGraph(attributePaths = "adventure")
    List<GameCharacterEntity> findByOwner_IdAndNpcFalse(UUID owner_id);

    @EntityGraph(attributePaths = "adventure")
    List<GameCharacterEntity> findByAdventure_Id(UUID adventure_id);


    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from GameCharacterEntity e where e.adventure.id = :adventureId")
    void deleteByAdventureId(@Param("adventureId") UUID adventureId);


    /** The visibility is applied in the query: nobody receives a private non-player character it must not know of. */
    @EntityGraph(attributePaths = "adventure")
    @Query("select e from GameCharacterEntity e where e.adventure.id = :adventureId and (e.npc = false or e.visibility = fr.barlords.dndgateapi.domain.functional.model.CharacterVisibility.PUBLIC)")
    List<GameCharacterEntity> findPublicByAdventureId(@Param("adventureId") UUID adventureId);

    long countByAdventure_IdAndNpcTrue(UUID adventureId);

    // The non-player characters stay: they belong to the adventure and go with it
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update GameCharacterEntity e set e.adventure = null, e.active = false where e.adventure.id = :adventureId and e.npc = false")
    void moveToReserveByAdventureId(@Param("adventureId") UUID adventureId);

    long countByOwner_IdAndAdventure_IdAndNpcFalse(UUID ownerId, UUID adventureId);

    @EntityGraph(attributePaths = "adventure")
    Optional<GameCharacterEntity> findByOwner_IdAndAdventure_IdAndActiveTrue(UUID ownerId, UUID adventureId);

    long countByOwner_IdAndAdventureIsNull(UUID ownerId);
}
