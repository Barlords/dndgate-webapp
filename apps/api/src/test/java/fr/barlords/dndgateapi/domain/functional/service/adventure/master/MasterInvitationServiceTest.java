package fr.barlords.dndgateapi.domain.functional.service.adventure.master;

import fr.barlords.dndgateapi.domain.functional.exception.adventure.AdventureOwnerException;
import fr.barlords.dndgateapi.domain.functional.exception.adventure.master.AdventureNotTransferableException;
import fr.barlords.dndgateapi.domain.functional.exception.adventure.master.AlreadyMasterException;
import fr.barlords.dndgateapi.domain.functional.exception.adventure.master.MasterInvitationNotFoundException;
import fr.barlords.dndgateapi.domain.functional.model.account.Account;
import fr.barlords.dndgateapi.domain.functional.model.adventure.Adventure;
import fr.barlords.dndgateapi.domain.functional.model.adventure.AdventureStatus;
import fr.barlords.dndgateapi.domain.functional.model.adventure.master.IssuedMasterInvitation;
import fr.barlords.dndgateapi.domain.functional.model.adventure.master.MasterInvitation;
import fr.barlords.dndgateapi.domain.functional.service.CurrentAccountService;
import fr.barlords.dndgateapi.domain.functional.service.adventure.AdventureOwnerVerifierService;
import fr.barlords.dndgateapi.domain.port.out.AdventurePersistenceSpi;
import fr.barlords.dndgateapi.domain.port.out.MasterInvitationPersistenceSpi;
import fr.barlords.dndgateapi.domain.port.out.OpaqueTokenSpi;
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
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MasterInvitationServiceTest {

    @Mock
    private AdventurePersistenceSpi adventures;

    @Mock
    private MasterInvitationPersistenceSpi invitations;

    @Mock
    private AdventureOwnerVerifierService ownerVerifier;

    @Mock
    private CurrentAccountService currentAccountService;

    @Mock
    private MasterHandOverService handOver;

    @Mock
    private OpaqueTokenSpi opaqueTokens;

    private final Instant now = Instant.parse("2026-03-01T10:00:00Z");

    private MasterInvitationService service;

    private final UUID master = UUID.randomUUID();

    private final Account candidate = Account.builder().pseudo("candidate").build();

    @BeforeEach
    void setUp() {
        service = new MasterInvitationService(adventures, invitations, ownerVerifier, currentAccountService, handOver, opaqueTokens,
                Clock.fixed(now, ZoneOffset.UTC), Duration.ofDays(7));
        lenient().when(opaqueTokens.generate()).thenReturn("the-secret-code");
        lenient().when(opaqueTokens.hash(any())).thenAnswer(i -> "hash-of-" + i.getArguments()[0]);
        lenient().when(currentAccountService.getAccountById(candidate.getId())).thenReturn(candidate);
    }

    private Adventure adventure(AdventureStatus status) {
        Adventure adventure = Adventure.builder().ownerId(master).status(status).build();
        lenient().when(ownerVerifier.verify(master, adventure.getId())).thenReturn(adventure);
        lenient().when(adventures.findById(adventure.getId())).thenReturn(Optional.of(adventure));
        return adventure;
    }

    private MasterInvitation invitationFor(Adventure adventure, Instant expiresAt) {
        MasterInvitation invitation = MasterInvitation.builder().adventureId(adventure.getId()).codeHash("hash-of-the-secret-code")
                .createdBy(master).createdAt(now.minusSeconds(60)).expiresAt(expiresAt).build();
        lenient().when(invitations.findByCodeHash("hash-of-the-secret-code")).thenReturn(Optional.of(invitation));
        lenient().when(invitations.consume(invitation.getId())).thenReturn(true);
        return invitation;
    }

    // ---- inviting ----

    @Test
    void the_code_is_given_once_and_only_its_hash_is_stored_with_an_expiry() {
        Adventure adventure = adventure(AdventureStatus.ONGOING);

        IssuedMasterInvitation issued = service.invite(master, adventure.getId());

        Assertions.assertEquals("the-secret-code", issued.code());
        Assertions.assertEquals(now.plus(Duration.ofDays(7)), issued.expiresAt());
        ArgumentCaptor<MasterInvitation> stored = ArgumentCaptor.forClass(MasterInvitation.class);
        verify(invitations).replace(stored.capture());
        Assertions.assertEquals("hash-of-the-secret-code", stored.getValue().getCodeHash());
        Assertions.assertEquals(adventure.getId(), stored.getValue().getAdventureId());
        Assertions.assertEquals(master, stored.getValue().getCreatedBy());
    }

    @Test
    void only_the_master_invites_or_revokes() {
        UUID id = UUID.randomUUID();
        when(ownerVerifier.verify(master, id)).thenThrow(new AdventureOwnerException());

        Assertions.assertThrows(AdventureOwnerException.class, () -> service.invite(master, id));
        Assertions.assertThrows(AdventureOwnerException.class, () -> service.revoke(master, id));
        verify(invitations, never()).replace(any());
        verify(invitations, never()).deleteByAdventureId(any());
    }

    @Test
    void no_invitation_for_an_adventure_that_is_over() {
        Adventure adventure = adventure(AdventureStatus.FINISH);

        Assertions.assertThrows(AdventureNotTransferableException.class, () -> service.invite(master, adventure.getId()));
        verify(invitations, never()).replace(any());
    }

    @Test
    void revoking_removes_the_pending_invitation() {
        Adventure adventure = adventure(AdventureStatus.ONGOING);

        service.revoke(master, adventure.getId());

        verify(invitations).deleteByAdventureId(adventure.getId());
    }

    // ---- accepting ----

    @Test
    void the_holder_of_a_valid_code_becomes_master() {
        Adventure adventure = adventure(AdventureStatus.ONGOING);
        invitationFor(adventure, now.plusSeconds(60));
        Adventure handedOver = adventure.withOwnerId(candidate.getId());
        when(handOver.handOver(adventure, candidate.getId())).thenReturn(handedOver);

        Assertions.assertSame(handedOver, service.accept(candidate.getId(), "the-secret-code"));
    }

    @Test
    void an_unknown_an_expired_and_a_used_code_all_look_the_same() {
        Adventure adventure = adventure(AdventureStatus.ONGOING);

        Assertions.assertThrows(MasterInvitationNotFoundException.class, () -> service.accept(candidate.getId(), "unknown"));

        invitationFor(adventure, now.minusSeconds(1));
        Assertions.assertThrows(MasterInvitationNotFoundException.class, () -> service.accept(candidate.getId(), "the-secret-code"));

        MasterInvitation valid = invitationFor(adventure, now.plusSeconds(60));
        when(invitations.consume(valid.getId())).thenReturn(false);
        Assertions.assertThrows(MasterInvitationNotFoundException.class, () -> service.accept(candidate.getId(), "the-secret-code"));

        verify(handOver, never()).handOver(any(), any());
    }

    @Test
    void a_code_of_an_adventure_without_master_or_over_does_not_work() {
        Adventure vacant = adventure(AdventureStatus.ONGOING).withMasterVacantSince(now);
        invitationFor(vacant, now.plusSeconds(60));
        when(adventures.findById(vacant.getId())).thenReturn(Optional.of(vacant));
        Assertions.assertThrows(MasterInvitationNotFoundException.class, () -> service.accept(candidate.getId(), "the-secret-code"));

        Adventure over = adventure(AdventureStatus.FINISH);
        invitationFor(over, now.plusSeconds(60));
        Assertions.assertThrows(MasterInvitationNotFoundException.class, () -> service.accept(candidate.getId(), "the-secret-code"));
    }

    @Test
    void the_master_cannot_accept_their_own_invitation_and_the_code_is_kept() {
        Adventure adventure = adventure(AdventureStatus.ONGOING).withOwnerId(candidate.getId());
        invitationFor(adventure, now.plusSeconds(60));
        when(adventures.findById(adventure.getId())).thenReturn(Optional.of(adventure));

        Assertions.assertThrows(AlreadyMasterException.class, () -> service.accept(candidate.getId(), "the-secret-code"));
        verify(invitations, never()).consume(any());
    }

    @Test
    void a_deleted_account_cannot_accept() {
        Account anonymous = Account.builder().anonymizedAt(now).build();
        when(currentAccountService.getAccountById(anonymous.getId())).thenReturn(anonymous);

        Assertions.assertThrows(MasterInvitationNotFoundException.class, () -> service.accept(anonymous.getId(), "the-secret-code"));
    }
}
