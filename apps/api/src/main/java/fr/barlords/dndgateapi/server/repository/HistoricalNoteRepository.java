package fr.barlords.dndgateapi.server.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import fr.barlords.dndgateapi.server.entity.HistoricalNoteEntity;
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
public interface HistoricalNoteRepository extends JpaRepository<HistoricalNoteEntity, UUID> {

    Page<HistoricalNoteEntity> findByAdventure_Id(UUID adventureId, Pageable pageable);


    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from HistoricalNoteEntity e where e.adventure.id = :adventureId")
    void deleteByAdventureId(@Param("adventureId") UUID adventureId);


    long countByAdventure_Id(UUID adventureId);
}
