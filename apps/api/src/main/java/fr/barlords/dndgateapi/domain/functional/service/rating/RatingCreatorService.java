package fr.barlords.dndgateapi.domain.functional.service.rating;

import fr.barlords.dndgateapi.domain.functional.exception.rating.RatingAlreadyExistsException;
import fr.barlords.dndgateapi.domain.functional.model.Rating;
import fr.barlords.dndgateapi.domain.port.in.rating.RatingCreatorApi;
import fr.barlords.dndgateapi.domain.port.out.RatingPersistenceSpi;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.UUID;

@Slf4j
@RequiredArgsConstructor
public class RatingCreatorService implements RatingCreatorApi {

    private final RatingPersistenceSpi spi;

    private final RatingEligibilityService ratingEligibilityService;

    @Override
    public Rating create(UUID accountId, Rating rating) {
        ratingEligibilityService.verify(accountId, rating.getAccountTargetedId());

        if (spi.findByEmitterIdAndTargetedAccountId(accountId, rating.getAccountTargetedId()).isPresent()) {
            throw new RatingAlreadyExistsException();
        }

        return spi.save(rating.withEmitterId(accountId));
    }

}
