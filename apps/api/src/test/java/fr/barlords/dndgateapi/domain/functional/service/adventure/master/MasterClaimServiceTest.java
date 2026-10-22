package fr.barlords.dndgateapi.domain.functional.service.adventure.master;

import fr.barlords.dndgateapi.domain.functional.exception.adventure.AdventureAccessDeniedException;
import fr.barlords.dndgateapi.domain.functional.exception.adventure.master.AdventureNotTransferableException;
import fr.barlords.dndgateapi.domain.functional.exception.adventure.master.MasterClaimAlreadyOpenException;
import fr.barlords.dndgateapi.domain.functional.exception.adventure.master.MasterClaimNotFoundException;
import fr.barlords.dndgateapi.domain.functional.exception.adventure.master.MasterSeatNotVacantException;
import fr.barlords.dndgateapi.domain.functional.exception.adventure.master.NotEligibleToClaimMasterException;
import fr.barlords.dndgateapi.domain.functional.exception.adventure.master.NotEligibleToVoteException;
import fr.barlords.dndgateapi.domain.functional.model.account.Account;
import fr.barlords.dndgateapi.domain.functional.model.adventure.Adventure;
import fr.barlords.dndgateapi.domain.functional.model.adventure.AdventureStatus;
import fr.barlords.dndgateapi.domain.functional.model.adventure.master.MasterClaim;
import fr.barlords.dndgateapi.domain.functional.model.adventure.master.MasterClaimDetails;
import fr.barlords.dndgateapi.domain.functional.model.adventure.master.MasterClaimStatus;
import fr.barlords.dndgateapi.domain.port.in.account.AccountFinderApi;
import fr.barlords.dndgateapi.domain.port.in.adventure.AdventureAccessVerifierApi;
import fr.barlords.dndgateapi.domain.port.out.AdventurePersistenceSpi;
import fr.barlords.dndgateapi.domain.port.out.MasterClaimPersistenceSpi;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MasterClaimServiceTest {

    @Mock
    private AdventurePersistenceSpi adventures;

    @Mock
    private MasterClaimPersistenceSpi claims;

    @Mock
    private AdventureAccessVerifierApi accessVerifier;

    @Mock
    private AccountFinderApi accountFinderApi;

    @Mock
    private MasterHandOverService handOver;

    @Mock
    private MasterTransferNotifier notifier;

    private final Instant now = Instant.parse("2026-03-01T10:00:00Z");

    private MasterClaimService service;

    private final UUID candidate = UUID.randomUUID();

    private final UUID alice = UUID.randomUUID();

    private final UUID bob = UUID.randomUUID();

    private final UUID stranger = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new MasterClaimService(adventures, claims, accessVerifier, accountFinderApi, handOver, notifier,
                Clock.fixed(now, ZoneOffset.UTC), Duration.ofDays(7));
        for (UUID id : List.of(candidate, alice, bob, stranger)) {
            exists(id, false);
        }
        lenient().when(claims.save(any(MasterClaim.class))).thenAnswer(i -> i.getArguments()[0]);
    }

    private void exists(UUID id, boolean anonymized) {
        lenient().when(accountFinderApi.findById(id)).thenReturn(Optional.of(Account.builder().id(id).anonymizedAt(anonymized ? now : null).build()));
    }

    private Adventure vacantAdventure(UUID... players) {
        Adventure adventure = Adventure.builder()
                .ownerId(UUID.randomUUID())
                .status(AdventureStatus.ONGOING)
                .masterVacantSince(now.minusSeconds(3600))
                .players(new ArrayList<>(List.of(players)))
                .build();
        lenient().when(adventures.findById(adventure.getId())).thenReturn(Optional.of(adventure));
        return adventure;
    }

    private MasterClaim openClaim(Adventure adventure, Instant expiresAt) {
        MasterClaim claim = MasterClaim.builder().adventureId(adventure.getId()).candidateId(candidate)
                .createdAt(now.minusSeconds(60)).expiresAt(expiresAt).build();
        lenient().when(claims.findOpenByAdventureId(adventure.getId())).thenReturn(Optional.of(claim));
        return claim;
    }

    private MasterClaim lastSaved() {
        ArgumentCaptor<MasterClaim> saved = ArgumentCaptor.forClass(MasterClaim.class);
        verify(claims, org.mockito.Mockito.atLeastOnce()).save(saved.capture());
        return saved.getValue();
    }

    // ---- asking for the role ----

    @Test
    void a_player_asks_for_the_role_and_the_other_players_have_to_vote() {
        Adventure adventure = vacantAdventure(candidate, alice, bob);

        MasterClaimDetails details = service.claim(candidate, adventure.getId());

        Assertions.assertEquals(MasterClaimStatus.OPEN, details.claim().getStatus());
        Assertions.assertEquals(candidate, details.claim().getCandidateId());
        Assertions.assertEquals(now.plus(Duration.ofDays(7)), details.claim().getExpiresAt());
        Assertions.assertEquals(List.of(alice, bob), details.voters());
        verify(handOver, never()).handOver(any(), any());
        verify(notifier).claimOpened(adventure, candidate, List.of(alice, bob));
    }

    @Test
    void players_whose_account_was_deleted_are_not_asked() {
        exists(bob, true);
        Adventure adventure = vacantAdventure(candidate, alice, bob);

        Assertions.assertEquals(List.of(alice), service.claim(candidate, adventure.getId()).voters());
    }

    @Test
    void a_player_alone_with_a_vacant_seat_takes_the_role_at_once() {
        Adventure adventure = vacantAdventure(candidate);

        MasterClaimDetails details = service.claim(candidate, adventure.getId());

        Assertions.assertEquals(MasterClaimStatus.ACCEPTED, details.claim().getStatus());
        verify(handOver).handOver(adventure, candidate);
        verify(notifier, never()).claimOpened(any(), any(), any());
    }

    @Test
    void only_a_player_with_an_account_can_ask() {
        Adventure adventure = vacantAdventure(alice);
        Assertions.assertThrows(NotEligibleToClaimMasterException.class, () -> service.claim(stranger, adventure.getId()));

        exists(alice, true);
        Assertions.assertThrows(NotEligibleToClaimMasterException.class, () -> service.claim(alice, adventure.getId()));
        verify(claims, never()).save(any());
    }

    @Test
    void the_role_cannot_be_asked_while_there_is_a_master() {
        Adventure adventure = vacantAdventure(candidate, alice).withMasterVacantSince(null);
        when(adventures.findById(adventure.getId())).thenReturn(Optional.of(adventure));

        Assertions.assertThrows(MasterSeatNotVacantException.class, () -> service.claim(candidate, adventure.getId()));
    }

    @Test
    void the_role_cannot_be_asked_in_an_adventure_that_is_over() {
        Adventure adventure = vacantAdventure(candidate, alice).withStatus(AdventureStatus.FINISH);
        when(adventures.findById(adventure.getId())).thenReturn(Optional.of(adventure));

        Assertions.assertThrows(AdventureNotTransferableException.class, () -> service.claim(candidate, adventure.getId()));
    }

    @Test
    void one_vote_at_a_time() {
        Adventure adventure = vacantAdventure(candidate, alice);
        openClaim(adventure, now.plusSeconds(60));

        Assertions.assertThrows(MasterClaimAlreadyOpenException.class, () -> service.claim(candidate, adventure.getId()));
    }

    @Test
    void a_vote_whose_time_is_over_is_closed_and_a_new_one_can_start() {
        Adventure adventure = vacantAdventure(candidate, alice);
        MasterClaim stale = openClaim(adventure, now.minusSeconds(1));

        MasterClaimDetails details = service.claim(candidate, adventure.getId());

        verify(claims).save(stale.withStatus(MasterClaimStatus.EXPIRED));
        Assertions.assertEquals(MasterClaimStatus.OPEN, details.claim().getStatus());
        verify(claims, times(2)).save(any());
    }

    // ---- voting ----

    @Test
    void the_role_changes_hands_when_every_player_approved() {
        Adventure adventure = vacantAdventure(candidate, alice, bob);
        MasterClaim claim = openClaimWith(adventure, alice, true);
        when(claims.findOpenByAdventureId(adventure.getId())).thenReturn(Optional.of(claim));

        MasterClaimDetails details = service.vote(bob, adventure.getId(), true);

        Assertions.assertEquals(MasterClaimStatus.ACCEPTED, details.claim().getStatus());
        verify(handOver).handOver(adventure, candidate);
        Assertions.assertEquals(claim.getId(), details.claim().getId());
    }

    @Test
    void while_someone_has_not_answered_the_vote_goes_on() {
        Adventure adventure = vacantAdventure(candidate, alice, bob);
        openClaim(adventure, now.plusSeconds(60));

        MasterClaimDetails details = service.vote(alice, adventure.getId(), true);

        Assertions.assertEquals(MasterClaimStatus.OPEN, details.claim().getStatus());
        Assertions.assertEquals(Boolean.TRUE, details.claim().getVotes().get(alice));
        verify(handOver, never()).handOver(any(), any());
    }

    @Test
    void one_refusal_ends_the_vote_even_after_approvals() {
        Adventure adventure = vacantAdventure(candidate, alice, bob);
        when(claims.findOpenByAdventureId(adventure.getId())).thenReturn(Optional.of(openClaimWith(adventure, alice, true)));

        MasterClaimDetails details = service.vote(bob, adventure.getId(), false);

        Assertions.assertEquals(MasterClaimStatus.REJECTED, details.claim().getStatus());
        Assertions.assertEquals(MasterClaimStatus.REJECTED, lastSaved().getStatus());
        verify(handOver, never()).handOver(any(), any());
        verify(notifier).claimRejected(adventure, candidate);
    }

    @Test
    void an_approval_can_be_changed_into_a_refusal() {
        Adventure adventure = vacantAdventure(candidate, alice, bob);
        when(claims.findOpenByAdventureId(adventure.getId())).thenReturn(Optional.of(openClaimWith(adventure, alice, true)));

        MasterClaimDetails details = service.vote(alice, adventure.getId(), false);

        Assertions.assertEquals(MasterClaimStatus.REJECTED, details.claim().getStatus());
    }

    private MasterClaim openClaimWith(Adventure adventure, UUID voter, boolean approves) {
        return MasterClaim.builder().adventureId(adventure.getId()).candidateId(candidate).createdAt(now.minusSeconds(60))
                .expiresAt(now.plusSeconds(60)).build().withVote(voter, approves);
    }

    @Test
    void the_candidate_and_strangers_cannot_vote() {
        Adventure adventure = vacantAdventure(candidate, alice);
        openClaim(adventure, now.plusSeconds(60));

        Assertions.assertThrows(NotEligibleToVoteException.class, () -> service.vote(candidate, adventure.getId(), true));
        Assertions.assertThrows(NotEligibleToVoteException.class, () -> service.vote(stranger, adventure.getId(), true));
        verify(handOver, never()).handOver(any(), any());
    }

    @Test
    void nobody_votes_without_a_vote_in_progress_or_after_its_time() {
        Adventure adventure = vacantAdventure(candidate, alice);
        when(claims.findOpenByAdventureId(adventure.getId())).thenReturn(Optional.empty());
        Assertions.assertThrows(MasterClaimNotFoundException.class, () -> service.vote(alice, adventure.getId(), true));

        openClaim(adventure, now.minusSeconds(1));
        Assertions.assertThrows(MasterClaimNotFoundException.class, () -> service.vote(alice, adventure.getId(), true));
        verify(handOver, never()).handOver(any(), any());
    }

    @Test
    void a_vote_is_over_when_the_situation_changed_the_candidate_left_or_someone_took_the_role() {
        Adventure candidateLeft = vacantAdventure(alice, bob);
        openClaim(candidateLeft, now.plusSeconds(60));
        Assertions.assertEquals(MasterClaimStatus.CANCELLED, service.vote(alice, candidateLeft.getId(), true).claim().getStatus());

        Adventure masterBack = vacantAdventure(candidate, alice).withMasterVacantSince(null);
        when(adventures.findById(masterBack.getId())).thenReturn(Optional.of(masterBack));
        openClaim(masterBack, now.plusSeconds(60));
        Assertions.assertEquals(MasterClaimStatus.CANCELLED, service.vote(alice, masterBack.getId(), true).claim().getStatus());

        verify(handOver, never()).handOver(any(), any());
    }

    @Test
    void a_vote_for_a_candidate_whose_account_was_deleted_is_cancelled_and_never_hands_over_the_role() {
        exists(candidate, true);
        Adventure adventure = vacantAdventure(candidate, alice);
        openClaim(adventure, now.plusSeconds(60));

        MasterClaimDetails details = service.vote(alice, adventure.getId(), true);

        Assertions.assertEquals(MasterClaimStatus.CANCELLED, details.claim().getStatus());
        verify(handOver, never()).handOver(any(), any());
    }

    // ---- cancelling and reading ----

    @Test
    void only_the_candidate_cancels() {
        Adventure adventure = vacantAdventure(candidate, alice);
        openClaim(adventure, now.plusSeconds(60));

        Assertions.assertThrows(NotEligibleToClaimMasterException.class, () -> service.cancel(alice, adventure.getId()));
        verify(claims, never()).save(any());

        service.cancel(candidate, adventure.getId());
        Assertions.assertEquals(MasterClaimStatus.CANCELLED, lastSaved().getStatus());
        verify(notifier).claimCancelled(adventure, candidate, List.of(alice));
    }

    @Test
    void participants_read_the_vote_in_progress_and_others_do_not() {
        Adventure adventure = vacantAdventure(candidate, alice);
        when(accessVerifier.verifyParticipant(alice, adventure.getId())).thenReturn(adventure);
        when(accessVerifier.verifyParticipant(stranger, adventure.getId())).thenThrow(new AdventureAccessDeniedException());
        MasterClaim claim = openClaim(adventure, now.plusSeconds(60));

        Optional<MasterClaimDetails> found = service.findOpen(alice, adventure.getId());

        Assertions.assertEquals(claim.getId(), found.orElseThrow().claim().getId());
        Assertions.assertEquals(List.of(alice), found.get().voters());
        Assertions.assertThrows(AdventureAccessDeniedException.class, () -> service.findOpen(stranger, adventure.getId()));
    }

    @Test
    void a_vote_whose_time_is_over_is_not_shown() {
        Adventure adventure = vacantAdventure(candidate, alice);
        when(accessVerifier.verifyParticipant(alice, adventure.getId())).thenReturn(adventure);
        openClaim(adventure, now.minusSeconds(1));

        Assertions.assertTrue(service.findOpen(alice, adventure.getId()).isEmpty());
    }
}
