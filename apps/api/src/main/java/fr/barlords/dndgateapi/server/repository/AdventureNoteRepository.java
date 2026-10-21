package fr.barlords.dndgateapi.server.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import fr.barlords.dndgateapi.domain.functional.model.adventure.note.NoteVisibility;

import fr.barlords.dndgateapi.server.entity.AdventureNoteEntity;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Repository
@Transactional(propagation = Propagation.MANDATORY)
public interface AdventureNoteRepository extends JpaRepository<AdventureNoteEntity, UUID> {

    Page<AdventureNoteEntity> findByAdventure_Id(UUID adventureId, Pageable pageable);

    Page<AdventureNoteEntity> findByAdventure_IdAndNoteVisibility(UUID adventureId, NoteVisibility visibility, Pageable pageable);


    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from AdventureNoteEntity e where e.adventure.id = :adventureId")
    void deleteByAdventureId(@Param("adventureId") UUID adventureId);

}
