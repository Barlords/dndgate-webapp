package fr.barlords.dndgateapi.domain.functional.service.adventure;

import fr.barlords.dndgateapi.domain.functional.exception.adventure.AdventureAccessDeniedException;
import fr.barlords.dndgateapi.domain.functional.exception.adventure.AdventureNotFoundException;
import fr.barlords.dndgateapi.domain.functional.model.adventure.Adventure;
import fr.barlords.dndgateapi.domain.port.out.AdventurePersistenceSpi;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class AdventureAccessVerifierServiceTest {

    @InjectMocks
    private AdventureAccessVerifierService service;

    @Mock
    private AdventurePersistenceSpi spi;

    private final UUID ownerId = UUID.randomUUID();

    private final UUID playerId = UUID.randomUUID();

    private Adventure adventure() {
        return Adventure.builder().ownerId(ownerId).players(new ArrayList<>(List.of(playerId))).build();
    }

    @Test
    void should_fail_when_adventure_does_not_exist() {
        UUID id = UUID.randomUUID();
        when(spi.findById(id)).thenReturn(Optional.empty());

        Assertions.assertThrows(AdventureNotFoundException.class, () -> service.verifyParticipant(ownerId, id));
    }

    @Test
    void should_accept_the_owner() {
        Adventure adventure = adventure();
        when(spi.findById(adventure.getId())).thenReturn(Optional.of(adventure));

        Assertions.assertEquals(adventure, service.verifyParticipant(ownerId, adventure.getId()));
    }

    @Test
    void should_accept_a_player() {
        Adventure adventure = adventure();
        when(spi.findById(adventure.getId())).thenReturn(Optional.of(adventure));

        Assertions.assertEquals(adventure, service.verifyParticipant(playerId, adventure.getId()));
    }

    @Test
    void should_reject_a_stranger() {
        Adventure adventure = adventure();
        when(spi.findById(adventure.getId())).thenReturn(Optional.of(adventure));

        Assertions.assertThrows(AdventureAccessDeniedException.class,
                () -> service.verifyParticipant(UUID.randomUUID(), adventure.getId()));
    }
}
