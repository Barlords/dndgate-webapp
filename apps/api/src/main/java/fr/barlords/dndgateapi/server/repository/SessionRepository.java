package fr.barlords.dndgateapi.server.repository;

import fr.barlords.dndgateapi.server.entity.SessionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Repository
@Transactional(propagation = Propagation.MANDATORY)
public interface SessionRepository extends JpaRepository<SessionEntity, UUID> {

    @Query("select count(s) > 0 from SessionEntity s where s.id = :id and s.revokedAt is null and s.expiresAt > :now")
    boolean isActive(@Param("id") UUID id, @Param("now") Instant now);

    @Modifying
    @Query("update SessionEntity s set s.revokedAt = :at where s.id = :id and s.revokedAt is null")
    int revoke(@Param("id") UUID id, @Param("at") Instant at);

    @Modifying
    @Query("update SessionEntity s set s.revokedAt = :at where s.accountId = :accountId and s.revokedAt is null")
    int revokeAllOfAccount(@Param("accountId") UUID accountId, @Param("at") Instant at);

    @Modifying
    @Query("delete from SessionEntity s where s.expiresAt < :before")
    int deleteExpired(@Param("before") Instant before);

}
