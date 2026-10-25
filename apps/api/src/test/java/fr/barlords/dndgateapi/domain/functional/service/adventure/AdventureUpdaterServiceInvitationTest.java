package fr.barlords.dndgateapi.domain.functional.service.adventure;

import fr.barlords.dndgateapi.domain.functional.exception.account.AccountNotFoundException;
import fr.barlords.dndgateapi.domain.functional.exception.adventure.AdventureNotJoinableException;
import fr.barlords.dndgateapi.domain.functional.exception.adventure.InvitationRateLimitException;
import fr.barlords.dndgateapi.domain.functional.exception.adventure.PlayersAlreadyInAdventureException;
import fr.barlords.dndgateapi.domain.functional.model.account.Account;
import fr.barlords.dndgateapi.domain.functional.model.adventure.Adventure;
import fr.barlords.dndgateapi.domain.functional.model.adventure.AdventureStatus;
import fr.barlords.dndgateapi.domain.functional.model.adventure.InvitationLimits;
import fr.barlords.dndgateapi.domain.functional.service.CurrentAccountService;
import fr.barlords.dndgateapi.domain.port.in.account.AccountFinderApi;
import fr.barlords.dndgateapi.domain.port.in.adventure.AdventureFinderApi;
import fr.barlords.dndgateapi.domain.port.out.AdventurePersistenceSpi;
import fr.barlords.dndgateapi.domain.port.out.EmailingSpi;
import fr.barlords.dndgateapi.domain.port.out.InvitationLogSpi;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdventureUpdaterServiceInvitationTest {

    private static final Instant NOW = Instant.parse("2026-10-21T12:00:00Z");

    private static final Duration SENDER_WINDOW = Duration.ofHours(1);

    private static final Duration RECIPIENT_WINDOW = Duration.ofHours(24);

    @Mock
    private AdventurePersistenceSpi spi;

    @Mock
    private AdventureFinderApi adventureFinderApi;

    @Mock
    private CurrentAccountService currentAccountService;

    @Mock
    private AdventureOwnerVerifierService adventureOwnerVerifierService;

    @Mock
    private AccountFinderApi accountFinderApi;

    @Mock
    private EmailingSpi emailingSpi;

    @Mock
    private InvitationLogSpi invitationLog;

    private AdventureUpdaterService service;

    private final Account master = Account.builder().pseudo("master").email("master@test.fr").build();

    private final Account invited = Account.builder().pseudo("invited").email("invited@test.fr").build();

    private Adventure adventure = anAdventure(AdventureStatus.NOT_STARTED);

    private Adventure anAdventure(AdventureStatus status, UUID... players) {
        return Adventure.builder()
                .id(UUID.randomUUID())
                .ownerId(master.getId())
                .status(status)
                .invitationCode("CODE")
                .maxPlayer(4)
                .players(new ArrayList<>(List.of(players)))
                .build();
    }

    @BeforeEach
    void setUp() {
        service = new AdventureUpdaterService(spi, adventureFinderApi, currentAccountService,
                adventureOwnerVerifierService, accountFinderApi, emailingSpi, invitationLog,
                Clock.fixed(NOW, ZoneOffset.UTC), new InvitationLimits(3, SENDER_WINDOW, RECIPIENT_WINDOW),
                org.mockito.Mockito.mock(fr.barlords.dndgateapi.domain.port.out.AccessRevocationSpi.class),
                org.mockito.Mockito.mock(fr.barlords.dndgateapi.domain.port.out.GameCharacterPersistenceSpi.class),
                org.mockito.Mockito.mock(fr.barlords.dndgateapi.domain.port.out.MinioSpi.class),
                org.mockito.Mockito.mock(fr.barlords.dndgateapi.domain.port.out.AuditSpi.class),
                org.mockito.Mockito.mock(fr.barlords.dndgateapi.domain.port.in.plan.EntitlementApi.class));
    }

    private void ready() {
        when(currentAccountService.getAccountById(master.getId())).thenReturn(master);
        when(adventureOwnerVerifierService.verify(master.getId(), adventure.getId())).thenReturn(adventure);
        when(accountFinderApi.findByPseudo("invited")).thenReturn(Optional.of(invited));
    }

    private void invite() {
        service.sendInvitationCode(master.getId(), adventure.getId(), " invited ");
    }

    private void nothingSent() {
        verify(emailingSpi, never()).sendInvitationCodeEmail(any(), any(), any());
        verify(invitationLog, never()).record(any(), any(), any(), any());
    }

    @Test
    void an_invitation_is_sent_and_remembered_with_the_current_time() {
        ready();
        when(invitationLog.findLastSentToRecipientSince(any(), any(), any())).thenReturn(Optional.empty());
        when(invitationLog.findSentAtBySenderSince(any(), any())).thenReturn(List.of());

        invite();

        verify(emailingSpi).sendInvitationCodeEmail(master, invited, "CODE");
        verify(invitationLog).record(master.getId(), invited.getId(), adventure.getId(), NOW);
    }

    @Test
    void the_limits_are_counted_over_their_own_windows() {
        ready();
        when(invitationLog.findLastSentToRecipientSince(invited.getId(), adventure.getId(), NOW.minus(RECIPIENT_WINDOW)))
                .thenReturn(Optional.empty());
        when(invitationLog.findSentAtBySenderSince(master.getId(), NOW.minus(SENDER_WINDOW))).thenReturn(List.of());

        invite();

        verify(emailingSpi).sendInvitationCodeEmail(master, invited, "CODE");
    }

    @Test
    void an_adventure_that_is_not_waiting_for_players_cannot_send_invitations() {
        for (AdventureStatus status : List.of(AdventureStatus.ONGOING, AdventureStatus.FINISH, AdventureStatus.ABORT)) {
            adventure = anAdventure(status);
            ready();

            Assertions.assertThrows(AdventureNotJoinableException.class, this::invite, status.name());
        }
        nothingSent();
    }

    @Test
    void someone_who_already_plays_cannot_be_invited() {
        adventure = anAdventure(AdventureStatus.NOT_STARTED, invited.getId());
        ready();

        Assertions.assertThrows(PlayersAlreadyInAdventureException.class, this::invite);
        nothingSent();
    }

    @Test
    void the_game_master_cannot_invite_himself() {
        ready();
        when(accountFinderApi.findByPseudo("invited")).thenReturn(Optional.of(master));

        Assertions.assertThrows(PlayersAlreadyInAdventureException.class, this::invite);
        nothingSent();
    }

    @Test
    void an_unknown_recipient_is_not_found() {
        when(currentAccountService.getAccountById(master.getId())).thenReturn(master);
        when(adventureOwnerVerifierService.verify(master.getId(), adventure.getId())).thenReturn(adventure);
        when(accountFinderApi.findByPseudo("invited")).thenReturn(Optional.empty());

        Assertions.assertThrows(AccountNotFoundException.class, this::invite);
        nothingSent();
    }

    @Test
    void a_recipient_invited_less_than_a_day_ago_is_refused_until_a_day_after_that_invitation() {
        ready();
        Instant previous = NOW.minus(Duration.ofHours(10));
        when(invitationLog.findLastSentToRecipientSince(invited.getId(), adventure.getId(), NOW.minus(RECIPIENT_WINDOW)))
                .thenReturn(Optional.of(previous));

        InvitationRateLimitException refused = Assertions.assertThrows(InvitationRateLimitException.class, this::invite);

        Assertions.assertEquals(previous.plus(RECIPIENT_WINDOW), refused.getRetryAt());
        Assertions.assertEquals(InvitationRateLimitException.RECIPIENT_MESSAGE, refused.getMessage());
        nothingSent();
    }

    @Test
    void a_sender_who_reached_the_limit_is_refused_until_the_oldest_invitation_leaves_the_window() {
        ready();
        when(invitationLog.findLastSentToRecipientSince(any(), any(), any())).thenReturn(Optional.empty());
        Instant oldest = NOW.minus(Duration.ofMinutes(50));
        when(invitationLog.findSentAtBySenderSince(master.getId(), NOW.minus(SENDER_WINDOW)))
                .thenReturn(List.of(oldest, NOW.minus(Duration.ofMinutes(30)), NOW.minus(Duration.ofMinutes(5))));

        InvitationRateLimitException refused = Assertions.assertThrows(InvitationRateLimitException.class, this::invite);

        Assertions.assertEquals(oldest.plus(SENDER_WINDOW), refused.getRetryAt());
        Assertions.assertEquals(InvitationRateLimitException.SENDER_MESSAGE, refused.getMessage());
        nothingSent();
    }

    @Test
    void a_sender_below_the_limit_can_still_invite() {
        ready();
        when(invitationLog.findLastSentToRecipientSince(any(), any(), any())).thenReturn(Optional.empty());
        when(invitationLog.findSentAtBySenderSince(any(), any()))
                .thenReturn(List.of(NOW.minus(Duration.ofMinutes(50)), NOW.minus(Duration.ofMinutes(5))));

        invite();

        verify(emailingSpi).sendInvitationCodeEmail(master, invited, "CODE");
    }

    @Test
    void a_sender_over_a_lowered_limit_waits_for_enough_invitations_to_expire() {
        ready();
        when(invitationLog.findLastSentToRecipientSince(any(), any(), any())).thenReturn(Optional.empty());
        Instant second = NOW.minus(Duration.ofMinutes(40));
        when(invitationLog.findSentAtBySenderSince(any(), any())).thenReturn(List.of(
                NOW.minus(Duration.ofMinutes(55)), NOW.minus(Duration.ofMinutes(50)), second,
                NOW.minus(Duration.ofMinutes(30)), NOW.minus(Duration.ofMinutes(5))));

        InvitationRateLimitException refused = Assertions.assertThrows(InvitationRateLimitException.class, this::invite);

        // 5 sent, 3 allowed: the third oldest one must expire before there are only 2 left in the window
        Assertions.assertEquals(second.plus(SENDER_WINDOW), refused.getRetryAt());
    }

    @Test
    void the_limit_per_sender_cannot_be_zero() {
        Assertions.assertThrows(IllegalArgumentException.class, () -> new InvitationLimits(0, SENDER_WINDOW, RECIPIENT_WINDOW));
    }

    @Test
    void both_invitation_windows_must_be_positive() {
        Assertions.assertThrows(IllegalArgumentException.class, () -> new InvitationLimits(3, null, RECIPIENT_WINDOW));
        Assertions.assertThrows(IllegalArgumentException.class, () -> new InvitationLimits(3, Duration.ZERO, RECIPIENT_WINDOW));
        Assertions.assertThrows(IllegalArgumentException.class, () -> new InvitationLimits(3, Duration.ofHours(-1), RECIPIENT_WINDOW));
        Assertions.assertThrows(IllegalArgumentException.class, () -> new InvitationLimits(3, SENDER_WINDOW, null));
        Assertions.assertThrows(IllegalArgumentException.class, () -> new InvitationLimits(3, SENDER_WINDOW, Duration.ZERO));
        Assertions.assertThrows(IllegalArgumentException.class, () -> new InvitationLimits(3, SENDER_WINDOW, Duration.ofHours(-1)));
    }

    @Test
    void old_invitations_are_purged_after_the_longest_window() {
        new InvitationLogCleanerService(invitationLog, Clock.fixed(NOW, ZoneOffset.UTC),
                new InvitationLimits(3, SENDER_WINDOW, RECIPIENT_WINDOW)).purgeExpired();
        new InvitationLogCleanerService(invitationLog, Clock.fixed(NOW, ZoneOffset.UTC),
                new InvitationLimits(3, Duration.ofDays(3), RECIPIENT_WINDOW)).purgeExpired();

        verify(invitationLog).deleteSentBefore(NOW.minus(RECIPIENT_WINDOW));
        verify(invitationLog).deleteSentBefore(NOW.minus(Duration.ofDays(3)));
    }
}
