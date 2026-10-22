package fr.barlords.dndgateapi.domain.functional.service.planning;

import fr.barlords.dndgateapi.domain.functional.model.Upserted;
import fr.barlords.dndgateapi.domain.functional.model.planning.Planning;
import fr.barlords.dndgateapi.domain.functional.service.adventure.AdventureOwnerVerifierService;
import fr.barlords.dndgateapi.domain.port.out.PlanningPersistenceSpi;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlanningUpdaterServiceTest {

    @Mock
    private PlanningPersistenceSpi spi;

    @Mock
    private AdventureOwnerVerifierService ownerVerifier;

    private final UUID accountId = UUID.randomUUID();

    private final Planning sent = Planning.builder().adventureId(UUID.randomUUID())
            .propositionDates(List.of(LocalDate.of(2027, 2, 8))).build();

    @Test
    void a_first_planning_is_saved_and_reported_as_created() {
        PlanningUpdaterService service = new PlanningUpdaterService(spi, ownerVerifier);
        when(spi.findByAdventureId(sent.getAdventureId())).thenReturn(Optional.empty());
        when(spi.save(sent)).thenReturn(sent);

        Upserted<Planning> result = service.createOrUpdate(accountId, sent);

        Assertions.assertTrue(result.created());
        Assertions.assertSame(sent, result.value());
        verify(ownerVerifier).verify(accountId, sent.getAdventureId());
    }

    @Test
    void an_existing_planning_gets_the_new_dates_and_is_reported_as_updated() {
        PlanningUpdaterService service = new PlanningUpdaterService(spi, ownerVerifier);
        Planning existing = Planning.builder().adventureId(sent.getAdventureId())
                .propositionDates(List.of(LocalDate.of(2027, 2, 1))).build();
        when(spi.findByAdventureId(sent.getAdventureId())).thenReturn(Optional.of(existing));
        when(spi.save(any())).thenAnswer(call -> call.getArgument(0));

        Upserted<Planning> result = service.createOrUpdate(accountId, sent);

        Assertions.assertFalse(result.created());
        Assertions.assertEquals(existing.getId(), result.value().getId());
        Assertions.assertEquals(sent.getPropositionDates(), result.value().getPropositionDates());
    }
}
