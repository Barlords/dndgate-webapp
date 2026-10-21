package fr.barlords.dndgateapi.server.repository;

import fr.barlords.dndgateapi.server.entity.SentInvitationEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@Transactional(propagation = Propagation.MANDATORY)
public interface SentInvitationRepository extends JpaRepository<SentInvitationEntity, UUID> {

    @Query("select i.sentAt from SentInvitationEntity i where i.senderId = :senderId and i.sentAt > :since order by i.sentAt asc")
    List<Instant> findSentAtBySenderSince(@Param("senderId") UUID senderId, @Param("since") Instant since);

    @Query("select max(i.sentAt) from SentInvitationEntity i where i.recipientId = :recipientId "
            + "and i.adventureId = :adventureId and i.sentAt > :since")
    Optional<Instant> findLastSentAt(@Param("recipientId") UUID recipientId, @Param("adventureId") UUID adventureId,
                                     @Param("since") Instant since);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from SentInvitationEntity i where i.sentAt < :before")
    int deleteSentBefore(@Param("before") Instant before);
}
