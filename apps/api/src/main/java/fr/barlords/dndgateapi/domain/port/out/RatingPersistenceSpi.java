package fr.barlords.dndgateapi.domain.port.out;

import fr.barlords.dndgateapi.domain.functional.model.pagination.Page;
import fr.barlords.dndgateapi.domain.functional.model.pagination.PageRequest;


import fr.barlords.dndgateapi.domain.functional.model.Rating;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RatingPersistenceSpi extends PersistenceSpi<Rating, UUID> {

    Page<Rating> findByTargetedAccountId(UUID targetedAccountId, PageRequest request);

    Optional<Rating> findByEmitterIdAndTargetedAccountId(UUID emitterId, UUID targetedAccountId);

    Optional<Double> computeAverageRatingOfAccount(UUID targetedAccountId);
}
