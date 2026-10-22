package fr.barlords.dndgateapi.server.repository;

import fr.barlords.dndgateapi.server.entity.PendingSignupEntity;
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
public interface PendingSignupRepository extends JpaRepository<PendingSignupEntity, UUID> {

    /**
     * One statement, so that two simultaneous signups for the same address cannot both win and none fails on the
     * unique index: 1 row written means this signup was recorded, 0 means a recent one was kept.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "insert into pending_signups (id, email, pseudo, password_hash, code_hash, created_at, expires_at) "
            + "values (:id, :email, :pseudo, :passwordHash, :codeHash, :createdAt, :expiresAt) "
            + "on conflict (email) do update set pseudo = excluded.pseudo, password_hash = excluded.password_hash, "
            + "code_hash = excluded.code_hash, created_at = excluded.created_at, expires_at = excluded.expires_at "
            + "where pending_signups.created_at <= :recentSince", nativeQuery = true)
    int saveUnlessRecent(@Param("id") UUID id, @Param("email") String email, @Param("pseudo") String pseudo,
                         @Param("passwordHash") String passwordHash, @Param("codeHash") String codeHash,
                         @Param("createdAt") Instant createdAt, @Param("expiresAt") Instant expiresAt,
                         @Param("recentSince") Instant recentSince);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from PendingSignupEntity s where s.expiresAt < :now")
    int deleteExpiredBefore(@Param("now") Instant now);
}
