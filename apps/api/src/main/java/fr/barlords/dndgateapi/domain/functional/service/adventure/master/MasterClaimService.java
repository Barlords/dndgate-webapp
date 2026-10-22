package fr.barlords.dndgateapi.domain.functional.service.adventure.master;

import fr.barlords.dndgateapi.domain.functional.exception.adventure.AdventureNotFoundException;
import fr.barlords.dndgateapi.domain.functional.exception.adventure.master.AdventureNotTransferableException;
import fr.barlords.dndgateapi.domain.functional.exception.adventure.master.MasterClaimAlreadyOpenException;
import fr.barlords.dndgateapi.domain.functional.exception.adventure.master.MasterClaimNotFoundException;
import fr.barlords.dndgateapi.domain.functional.exception.adventure.master.MasterSeatNotVacantException;
import fr.barlords.dndgateapi.domain.functional.exception.adventure.master.NotEligibleToClaimMasterException;
import fr.barlords.dndgateapi.domain.functional.exception.adventure.master.NotEligibleToVoteException;
import fr.barlords.dndgateapi.domain.functional.model.adventure.Adventure;
import fr.barlords.dndgateapi.domain.functional.model.adventure.master.MasterClaim;
import fr.barlords.dndgateapi.domain.functional.model.adventure.master.MasterClaimDetails;
import fr.barlords.dndgateapi.domain.functional.model.adventure.master.MasterClaimStatus;
import fr.barlords.dndgateapi.domain.port.in.account.AccountFinderApi;
import fr.barlords.dndgateapi.domain.port.in.adventure.AdventureAccessVerifierApi;
import fr.barlords.dndgateapi.domain.port.in.adventure.master.MasterClaimApi;
import fr.barlords.dndgateapi.domain.port.out.AdventurePersistenceSpi;
import fr.barlords.dndgateapi.domain.port.out.MasterClaimPersistenceSpi;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * When the game master is gone, a player can ask for the role. The other players vote and all of them have to
 * approve; a single refusal ends the vote, and a vote that stays unanswered ends when its time is over.
 * Players whose account was deleted are not asked.
 * <p>
 * Not transactional by itself: the caller wraps it in a transaction (the role and the vote change together).
 */
@Slf4j
@RequiredArgsConstructor
public class MasterClaimService implements MasterClaimApi {

    private final AdventurePersistenceSpi adventures;

    private final MasterClaimPersistenceSpi claims;

    private final AdventureAccessVerifierApi adventureAccessVerifierApi;

    private final AccountFinderApi accountFinderApi;

    private final MasterHandOverService handOverService;

    private final MasterTransferNotifier notifier;

    private final Clock clock;

    private final Duration timeToLive;

    @Override
    public MasterClaimDetails claim(UUID accountId, UUID adventureId) {
        Adventure adventure = adventures.findById(adventureId).orElseThrow(AdventureNotFoundException::new);

        if (!adventure.getPlayers().contains(accountId) || isAnonymous(accountId)) {
            throw new NotEligibleToClaimMasterException();
        }
        if (!adventure.isMasterVacant()) {
            throw new MasterSeatNotVacantException();
        }
        if (!adventure.isTransferable()) {
            throw new AdventureNotTransferableException();
        }

        Instant now = clock.instant();
        Optional<MasterClaim> pending = claims.findOpenByAdventureId(adventureId);
        if (pending.isPresent()) {
            if (isStillValid(adventure, pending.get(), now)) {
                throw new MasterClaimAlreadyOpenException();
            }
            close(pending.get(), MasterClaimStatus.EXPIRED);
        }

        MasterClaim claim = claims.save(MasterClaim.builder()
                .adventureId(adventureId)
                .candidateId(accountId)
                .createdAt(now)
                .expiresAt(now.plus(timeToLive))
                .build());

        log.info("Account {} asks for the game master role of the adventure {}", accountId, adventureId);

        MasterClaimDetails details = decide(adventure, claim);
        if (MasterClaimStatus.OPEN.equals(details.claim().getStatus())) {
            notifier.claimOpened(adventure, accountId, details.voters());
        }
        return details;
    }

    @Override
    public MasterClaimDetails vote(UUID accountId, UUID adventureId, boolean approves) {
        Adventure adventure = adventures.findById(adventureId).orElseThrow(AdventureNotFoundException::new);
        MasterClaim claim = openClaim(adventureId);

        // Things changed since the vote started (the candidate left, someone took the role...): it is over
        if (!isStillValid(adventure, claim, clock.instant())) {
            MasterClaim cancelled = close(claim, MasterClaimStatus.CANCELLED);
            return new MasterClaimDetails(cancelled, List.of());
        }

        if (!voters(adventure, claim).contains(accountId)) {
            throw new NotEligibleToVoteException();
        }

        if (!approves) {
            MasterClaim rejected = close(claim.withVote(accountId, false), MasterClaimStatus.REJECTED);
            notifier.claimRejected(adventure, claim.getCandidateId());
            return new MasterClaimDetails(rejected, voters(adventure, rejected));
        }

        return decide(adventure, claims.save(claim.withVote(accountId, true)));
    }

    @Override
    public void cancel(UUID accountId, UUID adventureId) {
        MasterClaim claim = openClaim(adventureId);

        if (!claim.getCandidateId().equals(accountId)) {
            throw new NotEligibleToClaimMasterException();
        }

        close(claim, MasterClaimStatus.CANCELLED);

        adventures.findById(adventureId).ifPresent(adventure ->
                notifier.claimCancelled(adventure, accountId, voters(adventure, claim)));
    }

    @Override
    public Optional<MasterClaimDetails> findOpen(UUID accountId, UUID adventureId) {
        Adventure adventure = adventureAccessVerifierApi.verifyParticipant(accountId, adventureId);

        return claims.findOpenByAdventureId(adventureId)
                .filter(claim -> claim.isOpenAt(clock.instant()))
                .map(claim -> new MasterClaimDetails(claim, voters(adventure, claim)));
    }

    /** The claim in progress; one whose time is over is closed on the way and is not in progress any more. */
    private MasterClaim openClaim(UUID adventureId) {
        MasterClaim claim = claims.findOpenByAdventureId(adventureId).orElseThrow(MasterClaimNotFoundException::new);

        if (!claim.isOpenAt(clock.instant())) {
            close(claim, MasterClaimStatus.EXPIRED);
            throw new MasterClaimNotFoundException();
        }
        return claim;
    }

    /** The role changes hands when everybody who has to vote approved, or at once when nobody has to. */
    private MasterClaimDetails decide(Adventure adventure, MasterClaim claim) {
        List<UUID> voters = voters(adventure, claim);

        if (!claim.isApprovedBy(voters)) {
            return new MasterClaimDetails(claim, voters);
        }

        MasterClaim accepted = close(claim, MasterClaimStatus.ACCEPTED);
        handOverService.handOver(adventure, claim.getCandidateId());

        log.info("Account {} became game master of the adventure {} by vote", claim.getCandidateId(), adventure.getId());

        return new MasterClaimDetails(accepted, voters);
    }

    private boolean isStillValid(Adventure adventure, MasterClaim claim, Instant now) {
        return claim.isOpenAt(now)
                && adventure.isMasterVacant()
                && adventure.isTransferable()
                && adventure.getPlayers().contains(claim.getCandidateId())
                && !isAnonymous(claim.getCandidateId());
    }

    /** The players but the candidate, whose account still exists. */
    private List<UUID> voters(Adventure adventure, MasterClaim claim) {
        return adventure.getPlayers().stream()
                .filter(player -> !player.equals(claim.getCandidateId()))
                .filter(player -> !isAnonymous(player))
                .toList();
    }

    private boolean isAnonymous(UUID accountId) {
        return accountFinderApi.findById(accountId).map(account -> account.isAnonymized()).orElse(true);
    }

    private MasterClaim close(MasterClaim claim, MasterClaimStatus status) {
        return claims.save(claim.withStatus(status));
    }
}
