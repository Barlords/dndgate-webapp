package fr.barlords.dndgateapi.domain.functional.model.adventure.master;

import lombok.Builder;
import lombok.Value;
import lombok.With;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * A player asks for the game master role of an adventure whose game master is gone.
 * The other players vote: all of them must approve; one refusal ends it.
 */
@Value
@Builder
public class MasterClaim {

    @Builder.Default
    @With
    UUID id = UUID.randomUUID();

    @With
    UUID adventureId;

    @With
    UUID candidateId;

    @With
    Instant createdAt;

    @With
    Instant expiresAt;

    @Builder.Default
    @With
    MasterClaimStatus status = MasterClaimStatus.OPEN;

    /** Voter -> approves. The candidate does not vote. */
    @Builder.Default
    @With
    Map<UUID, Boolean> votes = new HashMap<>();

    /** Read from the storage, null while never saved: it detects two people changing the vote at the same moment. */
    @With
    Long version;

    public boolean isOpenAt(Instant now) {
        return MasterClaimStatus.OPEN.equals(status) && expiresAt.isAfter(now);
    }

    public MasterClaim withVote(UUID voterId, boolean approves) {
        Map<UUID, Boolean> updated = new HashMap<>(votes);
        updated.put(voterId, approves);
        return withVotes(updated);
    }

    public boolean isApprovedBy(Iterable<UUID> voters) {
        for (UUID voter : voters) {
            if (!Boolean.TRUE.equals(votes.get(voter))) {
                return false;
            }
        }
        return true;
    }
}
