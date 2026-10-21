package fr.barlords.dndgateapi.server.repository;


import fr.barlords.dndgateapi.server.entity.AccountEntity;
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
public interface AccountRepository extends JpaRepository<AccountEntity, UUID> {

    Optional<AccountEntity> findByEmail(String email);

    Optional<AccountEntity> findByPseudoIgnoreCase(String pseudo);

    List<AccountEntity> findAllByPendingEmail(String pendingEmail);

    Optional<AccountEntity> findByVerificationCode(String code);

    /**
     * Atomic: concurrent attempts cannot make the counter lose increments.
     */
    @Modifying
    @Query("""
            update AccountEntity a
            set a.failedLoginAttempts = a.failedLoginAttempts + 1,
                a.lockedUntil = case when a.failedLoginAttempts + 1 >= :maxAttempts then :lockedUntil else a.lockedUntil end
            where a.id = :id
            """)
    int registerFailedLogin(@Param("id") UUID id, @Param("maxAttempts") int maxAttempts, @Param("lockedUntil") Instant lockedUntil);

    @Modifying
    @Query("update AccountEntity a set a.failedLoginAttempts = 0, a.lockedUntil = null where a.id = :id")
    int resetFailedLogins(@Param("id") UUID id);

}
