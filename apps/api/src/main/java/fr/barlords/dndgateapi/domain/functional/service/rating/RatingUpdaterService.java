package fr.barlords.dndgateapi.domain.functional.service.rating;

import fr.barlords.dndgateapi.domain.functional.model.Rating;
import fr.barlords.dndgateapi.domain.functional.model.Upserted;
import fr.barlords.dndgateapi.domain.port.in.rating.RatingUpdaterApi;
import fr.barlords.dndgateapi.domain.port.out.RatingPersistenceSpi;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.Clock;
import java.util.UUID;

@Slf4j
@RequiredArgsConstructor
public class RatingUpdaterService implements RatingUpdaterApi {

    private final RatingPersistenceSpi spi;

    private final RatingEligibilityService ratingEligibilityService;

    private final Clock clock;

    @Override
    public Upserted<Rating> createOrUpdate(UUID accountId, Rating rating) {
        ratingEligibilityService.verify(accountId, rating.getAccountTargetedId());

        return spi.findByEmitterIdAndTargetedAccountId(accountId, rating.getAccountTargetedId())
                .map(previous -> Upserted.updated(spi.save(previous.withRate(rating.getRate()))))
                .orElseGet(() -> Upserted.created(spi.save(rating.withEmitterId(accountId).withCreatedAt(clock.instant()))));
    }

}
