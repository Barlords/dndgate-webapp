package fr.barlords.dndgateapi.domain.functional.service.planning;

import fr.barlords.dndgateapi.domain.functional.exception.planning.PlanningAlreadyExistsException;
import fr.barlords.dndgateapi.domain.functional.model.planning.Planning;
import fr.barlords.dndgateapi.domain.functional.service.adventure.AdventureOwnerVerifierService;
import fr.barlords.dndgateapi.domain.port.out.PlanningPersistenceSpi;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlanningCreatorServiceTest {

    @Mock
    private PlanningPersistenceSpi spi;

    @Mock
    private AdventureOwnerVerifierService ownerVerifier;

    private final UUID accountId = UUID.randomUUID();

    private final Planning planning = Planning.builder().adventureId(UUID.randomUUID()).build();

    @Test
    void a_planning_is_saved_when_the_adventure_has_none() {
        PlanningCreatorService service = new PlanningCreatorService(spi, ownerVerifier);
        when(spi.findByAdventureId(planning.getAdventureId())).thenReturn(Optional.empty());
        when(spi.save(planning)).thenReturn(planning);

        Assertions.assertSame(planning, service.create(accountId, planning));
    }

    @Test
    void a_second_planning_for_the_same_adventure_is_refused_and_nothing_is_saved() {
        PlanningCreatorService service = new PlanningCreatorService(spi, ownerVerifier);
        when(spi.findByAdventureId(planning.getAdventureId())).thenReturn(Optional.of(planning));

        Assertions.assertThrows(PlanningAlreadyExistsException.class, () -> service.create(accountId, planning));

        verify(spi, never()).save(any());
    }
}
