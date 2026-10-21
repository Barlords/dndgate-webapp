package fr.barlords.dndgateapi.domain.functional.service.adventure;

import fr.barlords.dndgateapi.domain.functional.exception.adventure.AdventureConcurrentModificationException;
import fr.barlords.dndgateapi.domain.functional.exception.adventure.AdventureFullException;
import fr.barlords.dndgateapi.domain.functional.exception.adventure.PlayersAlreadyInAdventureException;
import fr.barlords.dndgateapi.domain.functional.model.account.Account;
import fr.barlords.dndgateapi.domain.functional.model.adventure.Adventure;
import fr.barlords.dndgateapi.domain.functional.model.adventure.AdventureStatus;
import fr.barlords.dndgateapi.domain.functional.model.adventure.AdventureVisibility;
import fr.barlords.dndgateapi.domain.functional.service.CurrentAccountService;
import fr.barlords.dndgateapi.domain.port.in.account.AccountFinderApi;
import fr.barlords.dndgateapi.domain.port.in.adventure.AdventureFinderApi;
import fr.barlords.dndgateapi.domain.port.out.AdventurePersistenceSpi;
import fr.barlords.dndgateapi.domain.port.out.EmailingSpi;
import io.vavr.control.Either;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** What happens when someone else changes the adventure between our read and our write. */
@ExtendWith(MockitoExtension.class)
class AdventureUpdaterServiceConcurrencyTest {

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

    private AdventureUpdaterService service;

    private final Account candidate = Account.builder().pseudo("candidate").email("candidate@test.fr").build();

    private final UUID other = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new AdventureUpdaterService(spi, adventureFinderApi, currentAccountService,
                adventureOwnerVerifierService, accountFinderApi, emailingSpi,
                org.mockito.Mockito.mock(fr.barlords.dndgateapi.domain.port.out.InvitationLogSpi.class), java.time.Clock.systemUTC(),
                new fr.barlords.dndgateapi.domain.functional.model.adventure.InvitationLimits(20, java.time.Duration.ofHours(1), java.time.Duration.ofHours(24)));
        lenient().when(currentAccountService.getAccountById(candidate.getId())).thenReturn(candidate);
    }

    private Adventure adventure(int maxPlayer, UUID... players) {
        return Adventure.builder()
                .ownerId(UUID.randomUUID())
                .status(AdventureStatus.NOT_STARTED)
                .visibility(AdventureVisibility.PUBLIC)
                .maxPlayer(maxPlayer)
                .players(new ArrayList<>(List.of(players)))
                .build();
    }

    @Test
    void a_join_refused_because_of_a_concurrent_change_is_replayed_from_a_fresh_read() {
        Adventure stale = adventure(4);
        Adventure fresh = stale.withPlayers(List.of(other));
        when(adventureFinderApi.findByIdVisibleTo(candidate.getId(), stale.getId()))
                .thenReturn(Optional.of(stale), Optional.of(fresh));
        when(spi.save(any(Adventure.class)))
                .thenThrow(new AdventureConcurrentModificationException(null))
                .thenAnswer(i -> Either.right(i.getArguments()[0]));

        Adventure joined = service.joinAdventure(stale.getId(), candidate.getId());

        Assertions.assertEquals(List.of(other, candidate.getId()), joined.getPlayers(), "the other player is not lost");
    }

    @Test
    void the_replay_sees_that_the_adventure_became_full() {
        Adventure stale = adventure(1);
        Adventure fresh = stale.withPlayers(List.of(other));
        when(adventureFinderApi.findByIdVisibleTo(candidate.getId(), stale.getId()))
                .thenReturn(Optional.of(stale), Optional.of(fresh));
        when(spi.save(any(Adventure.class))).thenThrow(new AdventureConcurrentModificationException(null));

        Assertions.assertThrows(AdventureFullException.class, () -> service.joinAdventure(stale.getId(), candidate.getId()));
        verify(spi, times(1)).save(any());
    }

    @Test
    void the_replay_sees_that_the_player_is_already_in() {
        Adventure stale = adventure(4);
        Adventure fresh = stale.withPlayers(List.of(candidate.getId()));
        when(adventureFinderApi.findByInvitationCode("code")).thenReturn(Optional.of(stale), Optional.of(fresh));
        when(spi.save(any(Adventure.class))).thenThrow(new AdventureConcurrentModificationException(null));

        Assertions.assertThrows(PlayersAlreadyInAdventureException.class, () -> service.joinAdventureByCode(candidate.getId(), "code"));
    }

    @Test
    void it_gives_up_after_a_few_attempts() {
        Adventure adventure = adventure(4);
        when(adventureFinderApi.findByIdVisibleTo(candidate.getId(), adventure.getId())).thenReturn(Optional.of(adventure));
        when(spi.save(any(Adventure.class))).thenThrow(new AdventureConcurrentModificationException(null));

        Assertions.assertThrows(AdventureConcurrentModificationException.class,
                () -> service.joinAdventure(adventure.getId(), candidate.getId()));
        verify(spi, times(10)).save(any());
    }

    @Test
    void quitting_is_replayed_too() {
        Adventure stale = adventure(4, candidate.getId());
        Adventure fresh = stale.withPlayers(List.of(candidate.getId(), other));
        when(adventureFinderApi.findById(stale.getId())).thenReturn(Optional.of(stale), Optional.of(fresh));
        when(spi.save(any(Adventure.class)))
                .thenThrow(new AdventureConcurrentModificationException(null))
                .thenAnswer(i -> Either.right(i.getArguments()[0]));

        Adventure left = service.quitAdventure(stale.getId(), candidate.getId());

        Assertions.assertEquals(List.of(other), left.getPlayers(), "the other player is not lost");
    }

    @Test
    void an_owner_edit_is_not_replayed_the_owner_is_told() {
        Adventure adventure = adventure(4);
        when(adventureOwnerVerifierService.verify(adventure.getOwnerId(), adventure.getId())).thenReturn(adventure);
        when(spi.save(any(Adventure.class))).thenThrow(new AdventureConcurrentModificationException(null));

        Assertions.assertThrows(AdventureConcurrentModificationException.class,
                () -> service.updateStatus(adventure.getOwnerId(), adventure.getId(), AdventureStatus.ONGOING));
        verify(spi, times(1)).save(any());
    }
}
