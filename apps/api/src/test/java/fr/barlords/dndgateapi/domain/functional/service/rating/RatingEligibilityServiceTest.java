package fr.barlords.dndgateapi.domain.functional.service.rating;

import fr.barlords.dndgateapi.domain.functional.exception.rating.NotPlayedTogetherException;
import fr.barlords.dndgateapi.domain.functional.exception.rating.SelfRatingException;
import fr.barlords.dndgateapi.domain.functional.model.adventure.Adventure;
import fr.barlords.dndgateapi.domain.functional.model.adventure.AdventureStatus;
import fr.barlords.dndgateapi.domain.port.out.AdventurePersistenceSpi;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
public class RatingEligibilityServiceTest {

    @InjectMocks
    private RatingEligibilityService service;

    @Mock
    private AdventurePersistenceSpi adventureSpi;

    private final UUID master = UUID.randomUUID();

    private final UUID player = UUID.randomUUID();

    private final UUID other = UUID.randomUUID();

    private Adventure adventure(AdventureStatus status) {
        return Adventure.builder().ownerId(master).status(status).players(new ArrayList<>(List.of(player))).build();
    }

    private void given(UUID emitter, List<Adventure> owned, List<Adventure> played) {
        lenient().when(adventureSpi.findAllByCreatorId(emitter)).thenReturn(owned);
        lenient().when(adventureSpi.findAllByPlayersId(emitter)).thenReturn(played);
    }

    @Test
    void should_refuse_to_rate_oneself() {
        Assertions.assertThrows(SelfRatingException.class, () -> service.verify(player, player));
    }

    @Test
    void a_player_can_rate_the_game_master_of_a_started_adventure() {
        given(player, List.of(), List.of(adventure(AdventureStatus.ONGOING)));

        Assertions.assertDoesNotThrow(() -> service.verify(player, master));
    }

    @Test
    void the_game_master_can_rate_a_player_of_a_finished_adventure() {
        given(master, List.of(adventure(AdventureStatus.FINISH)), List.of());

        Assertions.assertDoesNotThrow(() -> service.verify(master, player));
    }

    @Test
    void two_players_of_the_same_started_adventure_can_rate_each_other() {
        UUID secondPlayer = UUID.randomUUID();
        Adventure adventure = adventure(AdventureStatus.ONGOING);
        adventure.getPlayers().add(secondPlayer);
        given(player, List.of(), List.of(adventure));

        Assertions.assertDoesNotThrow(() -> service.verify(player, secondPlayer));
    }

    @Test
    void should_refuse_when_the_adventure_has_not_started() {
        given(player, List.of(), List.of(adventure(AdventureStatus.NOT_STARTED)));

        Assertions.assertThrows(NotPlayedTogetherException.class, () -> service.verify(player, master));
    }

    @Test
    void should_refuse_when_the_adventure_was_aborted() {
        given(player, List.of(), List.of(adventure(AdventureStatus.ABORT)));

        Assertions.assertThrows(NotPlayedTogetherException.class, () -> service.verify(player, master));
    }

    @Test
    void should_refuse_when_the_target_was_never_in_a_shared_adventure() {
        given(player, List.of(), List.of(adventure(AdventureStatus.ONGOING)));

        Assertions.assertThrows(NotPlayedTogetherException.class, () -> service.verify(player, other));
    }

    @Test
    void should_refuse_when_the_emitter_played_nothing() {
        given(other, List.of(), List.of());

        Assertions.assertThrows(NotPlayedTogetherException.class, () -> service.verify(other, master));
    }
}
