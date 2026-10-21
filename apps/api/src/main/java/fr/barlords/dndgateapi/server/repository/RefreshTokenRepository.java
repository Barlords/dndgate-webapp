package fr.barlords.dndgateapi.server.repository;

import fr.barlords.dndgateapi.server.entity.RefreshTokenEntity;
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
public interface RefreshTokenRepository extends JpaRepository<RefreshTokenEntity, UUID> {

    Optional<RefreshTokenEntity> findByTokenHash(String tokenHash);

    /**
     * Atomic compare-and-set: only one of two concurrent requests can consume the token.
     */
    @Modifying
    @Query("update RefreshTokenEntity t set t.usedAt = :at where t.id = :id and t.usedAt is null")
    int markUsed(@Param("id") UUID id, @Param("at") Instant at);

}
