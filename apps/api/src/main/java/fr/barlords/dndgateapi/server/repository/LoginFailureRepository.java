package fr.barlords.dndgateapi.server.repository;

import fr.barlords.dndgateapi.server.entity.LoginFailureEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
@Transactional(propagation = Propagation.MANDATORY)
public interface LoginFailureRepository extends JpaRepository<LoginFailureEntity, UUID> {

    Optional<LoginFailureEntity> findByAccountIdAndClientHash(UUID accountId, String clientHash);

    /**
     * One statement, so concurrent attempts cannot make the counter lose increments.
     * A lock that has expired restarts the count from one.
     */
    @Modifying
    @Query(nativeQuery = true, value = """
            insert into login_failures (id, account_id, client_hash, failed_attempts, locked_until, last_failed_at)
            values (gen_random_uuid(), :accountId, :clientHash, 1,
                    case when 1 >= :maxAttempts then cast(:lockedUntil as timestamp with time zone) end,
                    cast(:now as timestamp with time zone))
            on conflict (account_id, client_hash) do update set
                failed_attempts = case when login_failures.locked_until is not null
                                            and login_failures.locked_until <= cast(:now as timestamp with time zone)
                                       then 1 else login_failures.failed_attempts + 1 end,
                locked_until = case
                    when (case when login_failures.locked_until is not null
                                    and login_failures.locked_until <= cast(:now as timestamp with time zone)
                               then 1 else login_failures.failed_attempts + 1 end) >= :maxAttempts
                        then cast(:lockedUntil as timestamp with time zone)
                    when login_failures.locked_until is not null
                         and login_failures.locked_until <= cast(:now as timestamp with time zone)
                        then null
                    else login_failures.locked_until end,
                last_failed_at = cast(:now as timestamp with time zone)
            """)
    int register(@Param("accountId") UUID accountId, @Param("clientHash") String clientHash,
                 @Param("maxAttempts") int maxAttempts, @Param("lockedUntil") Instant lockedUntil, @Param("now") Instant now);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from LoginFailureEntity f where f.accountId = :accountId and f.clientHash = :clientHash")
    int deleteByAccountAndClient(@Param("accountId") UUID accountId, @Param("clientHash") String clientHash);

    @Query("select count(f) from LoginFailureEntity f where f.accountId = :accountId and f.lastFailedAt > :since")
    int countClientsFailingSince(@Param("accountId") UUID accountId, @Param("since") Instant since);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from LoginFailureEntity f where f.accountId = :accountId")
    int deleteByAccount(@Param("accountId") UUID accountId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from LoginFailureEntity f where f.lastFailedAt < :before")
    int deleteFailedBefore(@Param("before") Instant before);
}
