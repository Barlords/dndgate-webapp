package fr.barlords.dndgateapi.server.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import fr.barlords.dndgateapi.server.entity.RatingEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@Transactional(propagation = Propagation.MANDATORY)
public interface RatingRepository extends JpaRepository<RatingEntity, UUID> {

    Optional<RatingEntity> findByEmitterIdAndTargetId(UUID emitterId, UUID targetId);

    long countByTargetId(UUID targetId);

    Page<RatingEntity> findAllByTargetId(UUID targetId, Pageable pageable);

    @Query("SELECT AVG(r.rate) FROM RatingEntity r WHERE r.targetId = :accountId")
    Optional<Double> findAverageRatingByAccountId(@Param("accountId") UUID accountId);

}
