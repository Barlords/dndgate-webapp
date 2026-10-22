package fr.barlords.dndgateapi.bootstrap.config;

import fr.barlords.dndgateapi.domain.functional.exception.rating.RatingAlreadyExistsException;
import fr.barlords.dndgateapi.domain.functional.model.Rating;
import fr.barlords.dndgateapi.domain.functional.model.Upserted;
import fr.barlords.dndgateapi.domain.port.in.rating.RatingUpdaterApi;
import lombok.RequiredArgsConstructor;

import java.util.UUID;

/**
 * Rating with PUT is "find the rating of this account, then create or update it". When two first ratings of the
 * same account arrive together, the unique index refuses the second one (the adapter reports it as already rated):
 * the operation is replayed in a new transaction, where it now finds the rating and updates it.
 * Rating with POST is not replayed: there, an existing rating is a conflict.
 */
@RequiredArgsConstructor
public class TransactionalRatingUpdater implements RatingUpdaterApi {

    private final RatingUpdaterApi delegate;

    private final RetryingTransaction transaction;

    @Override
    public Upserted<Rating> createOrUpdate(UUID accountId, Rating rating) {
        return transaction.execute(() -> delegate.createOrUpdate(accountId, rating), RatingAlreadyExistsException.class);
    }
}
