package fr.barlords.dndgateapi.domain.functional.service.rating;

import fr.barlords.dndgateapi.domain.functional.exception.rating.NotPlayedTogetherException;
import fr.barlords.dndgateapi.domain.functional.exception.rating.SelfRatingException;
import fr.barlords.dndgateapi.domain.functional.model.adventure.Adventure;
import fr.barlords.dndgateapi.domain.port.out.AdventurePersistenceSpi;
import lombok.RequiredArgsConstructor;

import java.util.UUID;
import java.util.stream.Stream;

/**
 * Rule: an account can only rate another account it has played an adventure with,
 * that is an adventure that has started in which both are participants (owner or players).
 */
@RequiredArgsConstructor
public class RatingEligibilityService {

    private final AdventurePersistenceSpi adventureSpi;

    public void verify(UUID emitterId, UUID targetedAccountId) {
        if (emitterId.equals(targetedAccountId)) {
            throw new SelfRatingException();
        }

        boolean playedTogether = Stream.concat(
                        adventureSpi.findAllByCreatorId(emitterId).stream(),
                        adventureSpi.findAllByPlayersId(emitterId).stream())
                .filter(Adventure::hasBeenPlayed)
                .anyMatch(adventure -> adventure.isParticipant(targetedAccountId));

        if (!playedTogether) {
            throw new NotPlayedTogetherException();
        }
    }

}
