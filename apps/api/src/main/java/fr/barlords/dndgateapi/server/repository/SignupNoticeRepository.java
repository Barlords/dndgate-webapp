package fr.barlords.dndgateapi.server.repository;

import fr.barlords.dndgateapi.server.entity.SignupNoticeEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Repository
@Transactional(propagation = Propagation.MANDATORY)
public interface SignupNoticeRepository extends JpaRepository<SignupNoticeEntity, String> {

    /** One statement, so that two simultaneous claims cannot both win: 1 row written means the claim is won. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "insert into signup_notices (email_hash, sent_at) values (:hash, :now) "
            + "on conflict (email_hash) do update set sent_at = excluded.sent_at "
            + "where signup_notices.sent_at <= :windowStart", nativeQuery = true)
    int claim(@Param("hash") String hash, @Param("now") Instant now, @Param("windowStart") Instant windowStart);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from SignupNoticeEntity n where n.sentAt < :before")
    int deleteSentBefore(@Param("before") Instant before);
}
