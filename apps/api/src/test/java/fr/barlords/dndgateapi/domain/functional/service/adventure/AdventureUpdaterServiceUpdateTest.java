package fr.barlords.dndgateapi.domain.functional.service.adventure;

import fr.barlords.dndgateapi.domain.functional.exception.adventure.AdventureOwnerException;
import fr.barlords.dndgateapi.domain.functional.exception.adventure.InvalidStatusTransitionException;
import fr.barlords.dndgateapi.domain.functional.exception.adventure.MaxPlayerBelowPlayersException;
import fr.barlords.dndgateapi.domain.functional.model.adventure.Adventure;
import fr.barlords.dndgateapi.domain.functional.model.adventure.AdventureStatus;
import fr.barlords.dndgateapi.domain.functional.model.adventure.AdventureVisibility;
import fr.barlords.dndgateapi.domain.functional.service.CurrentAccountService;
import fr.barlords.dndgateapi.domain.port.in.account.AccountFinderApi;
import fr.barlords.dndgateapi.domain.port.in.adventure.AdventureFinderApi;
import fr.barlords.dndgateapi.domain.port.out.AdventurePersistenceSpi;
import fr.barlords.dndgateapi.domain.port.out.EmailingSpi;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdventureUpdaterServiceUpdateTest {

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

    private final UUID masterId = UUID.randomUUID();

    private final Adventure stored = Adventure.builder()
            .name("Lost Mine")
            .description("A classic")
            .status(AdventureStatus.NOT_STARTED)
            .visibility(AdventureVisibility.PRIVATE)
            .postalCode("75001")
            .city("Paris")
            .maxPlayer(4)
            .build();

    @BeforeEach
    void setUp() {
        service = new AdventureUpdaterService(spi, adventureFinderApi, currentAccountService,
                adventureOwnerVerifierService, accountFinderApi, emailingSpi,
                org.mockito.Mockito.mock(fr.barlords.dndgateapi.domain.port.out.InvitationLogSpi.class), java.time.Clock.systemUTC(),
                new fr.barlords.dndgateapi.domain.functional.model.adventure.InvitationLimits(20, java.time.Duration.ofHours(1), java.time.Duration.ofHours(24)),
                org.mockito.Mockito.mock(fr.barlords.dndgateapi.domain.port.out.AccessRevocationSpi.class));
    }

    private void masterIsOwner() {
        when(adventureOwnerVerifierService.verify(masterId, stored.getId())).thenReturn(stored);
        when(spi.save(any(Adventure.class))).thenAnswer(i -> i.getArguments()[0]);
    }

    // ---- update ----

    @Test
    void only_the_fields_that_are_given_change() {
        masterIsOwner();
        Adventure patch = Adventure.builder().id(stored.getId()).name("Renamed").city(null).build();

        Adventure result = service.update(masterId, patch);

        Assertions.assertEquals("Renamed", result.getName());
        Assertions.assertEquals("A classic", result.getDescription());
        Assertions.assertEquals(AdventureStatus.NOT_STARTED, result.getStatus());
        Assertions.assertEquals(AdventureVisibility.PRIVATE, result.getVisibility());
        Assertions.assertEquals("75001", result.getPostalCode());
        Assertions.assertEquals("Paris", result.getCity());
        Assertions.assertEquals(4, result.getMaxPlayer());
    }

    @Test
    void every_field_can_be_changed() {
        masterIsOwner();
        Adventure patch = Adventure.builder().id(stored.getId())
                .name("New").description("Other").visibility(AdventureVisibility.PUBLIC)
                .postalCode("69001").city("Lyon").maxPlayer(6).build();

        Adventure result = service.update(masterId, patch);

        Assertions.assertEquals("New", result.getName());
        Assertions.assertEquals("Other", result.getDescription());
        Assertions.assertEquals(AdventureVisibility.PUBLIC, result.getVisibility());
        Assertions.assertEquals("69001", result.getPostalCode());
        Assertions.assertEquals("Lyon", result.getCity());
        Assertions.assertEquals(6, result.getMaxPlayer());
    }

    @Test
    void the_status_is_never_taken_from_the_patch() {
        masterIsOwner();
        Adventure patch = Adventure.builder().id(stored.getId()).name("Renamed").status(AdventureStatus.FINISH).build();

        Adventure result = service.update(masterId, patch);

        Assertions.assertEquals(AdventureStatus.NOT_STARTED, result.getStatus());
    }

    @Test
    void the_capacity_cannot_drop_below_the_players_already_in() {
        Adventure twoPlayers = stored.withPlayers(java.util.List.of(UUID.randomUUID(), UUID.randomUUID()));
        when(adventureOwnerVerifierService.verify(masterId, stored.getId())).thenReturn(twoPlayers);

        Assertions.assertThrows(MaxPlayerBelowPlayersException.class,
                () -> service.update(masterId, Adventure.builder().id(stored.getId()).maxPlayer(1).build()));

        verify(spi, never()).save(any());
    }

    @Test
    void the_capacity_can_drop_to_exactly_the_number_of_players() {
        Adventure twoPlayers = stored.withPlayers(java.util.List.of(UUID.randomUUID(), UUID.randomUUID()));
        when(adventureOwnerVerifierService.verify(masterId, stored.getId())).thenReturn(twoPlayers);
        when(spi.save(any(Adventure.class))).thenAnswer(i -> i.getArguments()[0]);

        Adventure result = service.update(masterId, Adventure.builder().id(stored.getId()).maxPlayer(2).build());

        Assertions.assertEquals(2, result.getMaxPlayer());
    }

    @Test
    void the_identity_and_the_players_are_never_taken_from_the_patch() {
        masterIsOwner();
        Adventure patch = Adventure.builder().id(stored.getId()).name("Renamed").ownerId(UUID.randomUUID())
                .players(java.util.List.of(UUID.randomUUID())).build();

        Adventure result = service.update(masterId, patch);

        Assertions.assertEquals(stored.getOwnerId(), result.getOwnerId());
        Assertions.assertEquals(stored.getPlayers(), result.getPlayers());
    }

    @Test
    void someone_who_is_not_the_game_master_changes_nothing() {
        doThrow(new AdventureOwnerException()).when(adventureOwnerVerifierService).verify(masterId, stored.getId());

        Assertions.assertThrows(AdventureOwnerException.class,
                () -> service.update(masterId, Adventure.builder().id(stored.getId()).name("Hijacked").build()));

        verify(spi, never()).save(any());
    }

    @Test
    void a_failed_save_is_reported() {
        when(adventureOwnerVerifierService.verify(masterId, stored.getId())).thenReturn(stored);
        when(spi.save(any(Adventure.class))).thenThrow(new IllegalStateException("db down"));

        Assertions.assertThrows(IllegalStateException.class,
                () -> service.update(masterId, Adventure.builder().id(stored.getId()).name("X").build()));
    }

    // ---- visibility and status ----

    @Test
    void the_visibility_changes_and_nothing_else() {
        masterIsOwner();

        Adventure result = service.updateVisibility(masterId, stored.getId(), AdventureVisibility.PUBLIC);

        Assertions.assertEquals(AdventureVisibility.PUBLIC, result.getVisibility());
        Assertions.assertEquals(stored.withVisibility(AdventureVisibility.PUBLIC), result);
    }

    @Test
    void the_status_changes_and_nothing_else() {
        masterIsOwner();

        Adventure result = service.updateStatus(masterId, stored.getId(), AdventureStatus.ONGOING);

        Assertions.assertEquals(stored.withStatus(AdventureStatus.ONGOING), result);
    }

    @Test
    void every_status_move_follows_the_state_machine() {
        for (AdventureStatus from : AdventureStatus.values()) {
            for (AdventureStatus to : AdventureStatus.values()) {
                Adventure current = stored.withStatus(from);
                org.mockito.Mockito.reset(spi, adventureOwnerVerifierService);
                when(adventureOwnerVerifierService.verify(masterId, stored.getId())).thenReturn(current);
                org.mockito.Mockito.lenient().when(spi.save(any(Adventure.class))).thenAnswer(i -> i.getArguments()[0]);

                if (from == to) {
                    Assertions.assertEquals(current, service.updateStatus(masterId, stored.getId(), to), from + " to " + to);
                } else if (from.canMoveTo(to)) {
                    Assertions.assertEquals(to, service.updateStatus(masterId, stored.getId(), to).getStatus(), from + " to " + to);
                    verify(spi).save(any());
                } else {
                    InvalidStatusTransitionException refused = Assertions.assertThrows(InvalidStatusTransitionException.class,
                            () -> service.updateStatus(masterId, stored.getId(), to), from + " to " + to);
                    Assertions.assertTrue(refused.getMessage().contains(from.name()) && refused.getMessage().contains(to.name()));
                    verify(spi, never()).save(any());
                }
            }
        }
    }

    @Test
    void visibility_and_status_are_the_game_masters_only() {
        doThrow(new AdventureOwnerException()).when(adventureOwnerVerifierService).verify(masterId, stored.getId());

        Assertions.assertThrows(AdventureOwnerException.class,
                () -> service.updateVisibility(masterId, stored.getId(), AdventureVisibility.PUBLIC));
        Assertions.assertThrows(AdventureOwnerException.class,
                () -> service.updateStatus(masterId, stored.getId(), AdventureStatus.ABORT));

        verify(spi, never()).save(any());
    }
}
