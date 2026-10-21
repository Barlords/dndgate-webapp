package fr.barlords.dndgateapi.domain.functional.service.rating;

import fr.barlords.dndgateapi.domain.functional.model.pagination.Page;
import fr.barlords.dndgateapi.domain.functional.model.pagination.PageRequest;

import fr.barlords.dndgateapi.domain.functional.exception.rating.RatingOwnerException;
import fr.barlords.dndgateapi.domain.functional.model.Rating;
import fr.barlords.dndgateapi.domain.port.in.rating.RatingFinderApi;
import fr.barlords.dndgateapi.domain.port.out.RatingPersistenceSpi;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@RequiredArgsConstructor
public class RatingFinderService implements RatingFinderApi {

    private final RatingPersistenceSpi spi;

    @Override
    public Page<Rating> findByTargetedAccountId(UUID requesterId, UUID targetedAccountId, PageRequest request) {
        if (!requesterId.equals(targetedAccountId)) {
            throw new RatingOwnerException();
        }

        return spi.findByTargetedAccountId(targetedAccountId, request);
    }

    @Override
    public Optional<Rating> findByEmitterIdAndTargetedAccountId(UUID requesterId, UUID emitterId, UUID targetedAccountId) {
        if (!requesterId.equals(emitterId)) {
            throw new RatingOwnerException();
        }

        return spi.findByEmitterIdAndTargetedAccountId(emitterId, targetedAccountId);
    }

    @Override
    public Optional<Double> findAverageRatingOfAccount(UUID targetedAccountId) {
        return spi.computeAverageRatingOfAccount(targetedAccountId);
    }

}
