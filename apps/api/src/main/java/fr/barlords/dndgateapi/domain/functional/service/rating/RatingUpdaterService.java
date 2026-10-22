package fr.barlords.dndgateapi.domain.functional.service.rating;

import fr.barlords.dndgateapi.domain.functional.model.Rating;
import fr.barlords.dndgateapi.domain.port.in.rating.RatingUpdaterApi;
import fr.barlords.dndgateapi.domain.port.out.RatingPersistenceSpi;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.UUID;

@Slf4j
@RequiredArgsConstructor
public class RatingUpdaterService implements RatingUpdaterApi {

    private final RatingPersistenceSpi spi;

    private final RatingEligibilityService ratingEligibilityService;

    @Override
    public Rating createOrUpdate(UUID accountId, Rating rating) {
        ratingEligibilityService.verify(accountId, rating.getAccountTargetedId());

        Rating toSave = spi.findByEmitterIdAndTargetedAccountId(accountId, rating.getAccountTargetedId())
                .map(previous -> previous.withRate(rating.getRate()))
                .orElseGet(() -> rating.withEmitterId(accountId));

        return spi.save(toSave);
    }

}
