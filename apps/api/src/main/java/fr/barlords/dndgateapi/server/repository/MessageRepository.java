package fr.barlords.dndgateapi.server.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import fr.barlords.dndgateapi.server.entity.MessageEntity;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@Transactional(propagation = Propagation.MANDATORY)
public interface MessageRepository extends JpaRepository<MessageEntity, UUID> {

    Optional<MessageEntity> findMessageEntityById(UUID id);

    Page<MessageEntity> findByAdventureId(UUID adventureId, Pageable pageable);

    long countByAdventureId(UUID adventureId);

    List<MessageEntity> findMessageEntitiesBySenderIdOrderBySendDate(UUID senderId);


    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from MessageEntity m where m.adventureId = :adventureId")
    void deleteByAdventureId(@Param("adventureId") UUID adventureId);


    @Query("select count(m) from MessageEntity m where m.senderId = :senderId and m.sendDate > :since")
    long countBySenderIdSince(@Param("senderId") UUID senderId, @Param("since") Instant since);

    @Query("select min(m.sendDate) from MessageEntity m where m.senderId = :senderId and m.sendDate > :since")
    Optional<Instant> findOldestSendDateBySenderIdSince(@Param("senderId") UUID senderId, @Param("since") Instant since);
}
