package fr.barlords.dndgateapi.domain.port.in.rating;

import fr.barlords.dndgateapi.domain.functional.model.pagination.Page;
import fr.barlords.dndgateapi.domain.functional.model.pagination.PageRequest;

import fr.barlords.dndgateapi.domain.functional.model.Rating;
import fr.barlords.dndgateapi.domain.functional.model.RatingSummary;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RatingFinderApi {

    /**
     * The detailed ratings an account received are only readable by that account.
     */
    Page<Rating> findByTargetedAccountId(UUID requesterId, UUID targetedAccountId, PageRequest request);

    /**
     * A caller can only read the ratings it gave itself.
     */
    Optional<Rating> findByEmitterIdAndTargetedAccountId(UUID requesterId, UUID emitterId, UUID targetedAccountId);

    /**
     * The average is public: it is displayed next to the pseudo of every participant.
     */
    RatingSummary summarizeRatingsOfAccount(UUID targetedAccountId);

}
