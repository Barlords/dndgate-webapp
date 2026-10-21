package fr.barlords.dndgateapi.domain.functional.model.adventure.master;

import java.util.List;
import java.util.UUID;

/** A claim and the players who have to vote on it. */
public record MasterClaimDetails(MasterClaim claim, List<UUID> voters) {
}
