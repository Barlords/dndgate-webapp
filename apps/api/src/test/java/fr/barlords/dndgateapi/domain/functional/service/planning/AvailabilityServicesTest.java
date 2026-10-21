package fr.barlords.dndgateapi.domain.functional.service.planning;

import fr.barlords.dndgateapi.domain.ApplicationError;
import fr.barlords.dndgateapi.domain.functional.exception.DataNotSaveException;
import fr.barlords.dndgateapi.domain.functional.model.planning.Availability;
import fr.barlords.dndgateapi.domain.port.in.planning.AvailabilityUpdaterApi;
import fr.barlords.dndgateapi.domain.port.out.AvailabilityPersistenceSpi;
import io.vavr.control.Either;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AvailabilityServicesTest {

    @Mock
    private AvailabilityPersistenceSpi spi;

    @Mock
    private PlanningVisibilityVerifierService verifier;

    private final UUID accountId = UUID.randomUUID();

    private final UUID planningId = UUID.randomUUID();

    private final List<LocalDate> newDates = List.of(LocalDate.of(2027, 2, 8));

    private Availability received() {
        return Availability.builder().planningId(planningId).accountId(UUID.randomUUID()).availabilityDates(newDates).build();
    }

    @Test
    void a_first_vote_is_saved_for_the_caller_and_the_saved_vote_is_returned() {
        AvailabilityUpdaterService service = new AvailabilityUpdaterService(spi, verifier);
        when(spi.findByAccountIdAndPlanningId(accountId, planningId)).thenReturn(List.of());
        Availability stored = Availability.builder().planningId(planningId).accountId(accountId).availabilityDates(newDates).build();
        when(spi.save(any())).thenReturn(Either.right(stored));

        Assertions.assertSame(stored, service.createOrUpdate(accountId, received()));

        ArgumentCaptor<Availability> saved = ArgumentCaptor.forClass(Availability.class);
        verify(spi).save(saved.capture());
        Assertions.assertEquals(accountId, saved.getValue().getAccountId());
    }

    @Test
    void a_second_vote_replaces_the_dates_of_the_existing_one_and_returns_it() {
        AvailabilityUpdaterService service = new AvailabilityUpdaterService(spi, verifier);
        Availability previous = Availability.builder().planningId(planningId).accountId(accountId)
                .availabilityDates(List.of(LocalDate.of(2027, 2, 1))).build();
        when(spi.findByAccountIdAndPlanningId(accountId, planningId)).thenReturn(List.of(previous));
        when(spi.save(any())).thenAnswer(call -> Either.right(call.getArgument(0)));

        Availability result = service.createOrUpdate(accountId, received());

        Assertions.assertEquals(previous.getId(), result.getId());
        Assertions.assertEquals(newDates, result.getAvailabilityDates());
    }

    @Test
    void a_failed_write_is_reported() {
        AvailabilityUpdaterService service = new AvailabilityUpdaterService(spi, verifier);
        when(spi.findByAccountIdAndPlanningId(accountId, planningId)).thenReturn(List.of());
        when(spi.save(any())).thenReturn(Either.left(new ApplicationError("boom", null, null, null)));

        Assertions.assertThrows(DataNotSaveException.class, () -> service.createOrUpdate(accountId, received()));
    }

    @Test
    void creating_a_vote_is_creating_or_replacing_it() {
        AvailabilityUpdaterApi updater = org.mockito.Mockito.mock(AvailabilityUpdaterApi.class);
        Availability given = received();
        Availability stored = given.withAccountId(accountId);
        when(updater.createOrUpdate(accountId, given)).thenReturn(stored);

        Assertions.assertSame(stored, new AvailabilityCreatorService(updater).create(accountId, given));
    }
}
