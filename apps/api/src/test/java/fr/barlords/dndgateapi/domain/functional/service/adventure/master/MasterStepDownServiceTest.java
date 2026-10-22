package fr.barlords.dndgateapi.domain.functional.service.adventure.master;

import fr.barlords.dndgateapi.domain.functional.exception.DeletionNotConfirmedException;
import fr.barlords.dndgateapi.domain.functional.exception.adventure.AdventureOwnerException;
import fr.barlords.dndgateapi.domain.functional.exception.adventure.master.AdventureNotTransferableException;
import fr.barlords.dndgateapi.domain.functional.model.adventure.Adventure;
import fr.barlords.dndgateapi.domain.functional.model.adventure.AdventureStatus;
import fr.barlords.dndgateapi.domain.functional.service.adventure.AdventureOwnerVerifierService;
import fr.barlords.dndgateapi.domain.port.out.AdventurePersistenceSpi;
import fr.barlords.dndgateapi.domain.port.out.MasterInvitationPersistenceSpi;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MasterStepDownServiceTest {

    @Mock
    private AdventurePersistenceSpi adventures;

    @Mock
    private MasterInvitationPersistenceSpi invitations;

    @Mock
    private AdventureOwnerVerifierService ownerVerifier;

    @Mock
    private MasterTransferNotifier notifier;

    private final Instant now = Instant.parse("2026-03-01T10:00:00Z");

    private MasterStepDownService service;

    private final UUID master = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new MasterStepDownService(adventures, invitations, ownerVerifier, notifier, Clock.fixed(now, ZoneOffset.UTC));
        lenient().when(adventures.save(any(Adventure.class))).thenAnswer(i -> i.getArguments()[0]);
    }

    private Adventure adventure(AdventureStatus status) {
        Adventure adventure = Adventure.builder().ownerId(master).name("The lost mine").status(status).build();
        lenient().when(ownerVerifier.verify(master, adventure.getId())).thenReturn(adventure);
        return adventure;
    }

    @Test
    void the_master_steps_down_after_retyping_the_name_of_the_adventure() {
        Adventure adventure = adventure(AdventureStatus.ONGOING);

        Adventure result = service.stepDown(master, adventure.getId(), "The lost mine");

        Assertions.assertEquals(now, result.getMasterVacantSince());
        Assertions.assertFalse(result.isMaster(master), "a master who stepped down has no right left");
        verify(invitations).deleteByAdventureId(adventure.getId());
        verify(notifier).masterSteppedDown(result);
    }

    @Test
    void without_the_right_name_nothing_happens() {
        Adventure adventure = adventure(AdventureStatus.ONGOING);

        Assertions.assertThrows(DeletionNotConfirmedException.class, () -> service.stepDown(master, adventure.getId(), "the lost mine"));
        Assertions.assertThrows(DeletionNotConfirmedException.class, () -> service.stepDown(master, adventure.getId(), null));
        verify(adventures, never()).save(any());
        verify(notifier, never()).masterSteppedDown(any());
    }

    @Test
    void only_the_master_can_step_down() {
        UUID id = UUID.randomUUID();
        when(ownerVerifier.verify(master, id)).thenThrow(new AdventureOwnerException());

        Assertions.assertThrows(AdventureOwnerException.class, () -> service.stepDown(master, id, "x"));
        verify(adventures, never()).save(any());
    }

    @Test
    void a_finished_or_aborted_adventure_does_not_change_master() {
        for (AdventureStatus status : new AdventureStatus[]{AdventureStatus.FINISH, AdventureStatus.ABORT}) {
            Adventure adventure = adventure(status);

            Assertions.assertThrows(AdventureNotTransferableException.class, () -> service.stepDown(master, adventure.getId(), "The lost mine"));
        }
        verify(adventures, never()).save(any());
    }
}
