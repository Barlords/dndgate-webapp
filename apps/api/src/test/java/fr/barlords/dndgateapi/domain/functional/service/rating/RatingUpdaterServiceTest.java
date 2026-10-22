package fr.barlords.dndgateapi.domain.functional.service.rating;

import fr.barlords.dndgateapi.domain.functional.model.Rating;
import fr.barlords.dndgateapi.domain.functional.model.Upserted;
import fr.barlords.dndgateapi.domain.port.out.RatingPersistenceSpi;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RatingUpdaterServiceTest {

    @Mock
    private RatingPersistenceSpi spi;

    @Mock
    private RatingEligibilityService eligibility;

    private final Instant now = Instant.parse("2026-05-01T10:00:00Z");

    private final Clock clock = Clock.fixed(now, ZoneOffset.UTC);

    private final UUID emitterId = UUID.randomUUID();

    private final UUID targetId = UUID.randomUUID();

    private final Rating sent = Rating.builder().accountTargetedId(targetId).rate(4).build();

    @Test
    void a_first_rating_is_saved_for_the_caller_and_reported_as_created() {
        RatingUpdaterService service = new RatingUpdaterService(spi, eligibility, clock);
        when(spi.findByEmitterIdAndTargetedAccountId(emitterId, targetId)).thenReturn(Optional.empty());
        when(spi.save(any())).thenAnswer(call -> call.getArgument(0));

        Upserted<Rating> result = service.createOrUpdate(emitterId, sent);

        Assertions.assertTrue(result.created());
        Assertions.assertEquals(now, result.value().getCreatedAt());
        Assertions.assertEquals(emitterId, result.value().getEmitterId());
        verify(eligibility).verify(emitterId, targetId);
    }

    @Test
    void an_existing_rating_gets_the_new_rate_and_is_reported_as_updated() {
        RatingUpdaterService service = new RatingUpdaterService(spi, eligibility, clock);
        Instant firstRated = Instant.parse("2026-01-01T10:00:00Z");
        Rating previous = Rating.builder().emitterId(emitterId).accountTargetedId(targetId).rate(1).createdAt(firstRated).build();
        when(spi.findByEmitterIdAndTargetedAccountId(emitterId, targetId)).thenReturn(Optional.of(previous));
        when(spi.save(any())).thenAnswer(call -> call.getArgument(0));

        Upserted<Rating> result = service.createOrUpdate(emitterId, sent);

        Assertions.assertFalse(result.created());
        Assertions.assertEquals(previous.getId(), result.value().getId());
        Assertions.assertEquals(4, result.value().getRate());
        Assertions.assertEquals(firstRated, result.value().getCreatedAt());
    }
}
