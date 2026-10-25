package fr.barlords.dndgateapi.domain.functional.service.adventure;

import fr.barlords.dndgateapi.domain.port.out.AuditSpi;
import fr.barlords.dndgateapi.domain.functional.exception.DeletionNotConfirmedException;
import fr.barlords.dndgateapi.domain.functional.exception.adventure.AdventureInProgressException;
import fr.barlords.dndgateapi.domain.functional.exception.adventure.AdventureOwnerException;
import fr.barlords.dndgateapi.domain.functional.model.GameCharacter;
import fr.barlords.dndgateapi.domain.functional.model.adventure.Adventure;
import fr.barlords.dndgateapi.domain.functional.model.adventure.AdventureStatus;
import fr.barlords.dndgateapi.domain.functional.model.planning.Planning;
import fr.barlords.dndgateapi.domain.port.out.AccessRevocationSpi;
import fr.barlords.dndgateapi.domain.port.out.AdventurePersistenceSpi;
import fr.barlords.dndgateapi.domain.port.out.AvailabilityPersistenceSpi;
import fr.barlords.dndgateapi.domain.port.out.GameCharacterPersistenceSpi;
import fr.barlords.dndgateapi.domain.port.out.MasterClaimPersistenceSpi;
import fr.barlords.dndgateapi.domain.port.out.MasterInvitationPersistenceSpi;
import fr.barlords.dndgateapi.domain.port.out.MessagePersistenceSpi;
import fr.barlords.dndgateapi.domain.port.out.MinioSpi;
import fr.barlords.dndgateapi.domain.port.out.PlanningPersistenceSpi;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class AdventureDeleterServiceTest {

    @Mock
    private AdventurePersistenceSpi spi;

    @Mock
    private AdventureOwnerVerifierService ownerVerifier;

    @Mock
    private GameCharacterPersistenceSpi gameCharacters;

    @Mock
    private MessagePersistenceSpi messages;

    @Mock
    private PlanningPersistenceSpi plannings;

    @Mock
    private AvailabilityPersistenceSpi availabilities;

    @Mock
    private MinioSpi minioSpi;

    private AdventureDeleterService service;

    private final UUID accountId = UUID.randomUUID();

    private final Adventure adventure = Adventure.builder()
            .ownerId(accountId).name("La quête du soir").status(AdventureStatus.NOT_STARTED).build();

    @Mock
    private AuditSpi audit;

    @Mock
    private AccessRevocationSpi accessRevocation;

    @Mock
    private MasterClaimPersistenceSpi masterClaims;

    @Mock
    private MasterInvitationPersistenceSpi masterInvitations;

    @BeforeEach
    void setUp() {
        service = new AdventureDeleterService(spi, ownerVerifier, gameCharacters, messages, plannings, availabilities, minioSpi, audit, accessRevocation,
                masterClaims, masterInvitations);
    }

    private void ownerIsVerified() {
        when(ownerVerifier.verify(accountId, adventure.getId())).thenReturn(adventure);
    }

    private void nothingWasDeleted() {
        verify(spi, never()).deleteById(any());
        verify(messages, never()).deleteByAdventureId(any());
        verify(masterClaims, never()).deleteByAdventureId(any());
        verify(masterInvitations, never()).deleteByAdventureId(any());
        verify(plannings, never()).deleteById(any());
        verify(availabilities, never()).deleteByPlanningId(any());
        verifyNoInteractions(accessRevocation);
    }

    // ---- who and how ----

    @Test
    void someone_who_does_not_own_the_adventure_cannot_delete_it_whatever_the_confirmation() {
        doThrow(new AdventureOwnerException()).when(ownerVerifier).verify(accountId, adventure.getId());

        Assertions.assertThrows(AdventureOwnerException.class,
                () -> service.deleteById(accountId, adventure.getId(), "La quête du soir"));

        nothingWasDeleted();
        verifyNoInteractions(minioSpi);
    }

    @Test
    void a_wrong_name_deletes_nothing() {
        ownerIsVerified();

        Assertions.assertThrows(DeletionNotConfirmedException.class,
                () -> service.deleteById(accountId, adventure.getId(), "La quête du matin"));
        Assertions.assertThrows(DeletionNotConfirmedException.class,
                () -> service.deleteById(accountId, adventure.getId(), null));

        nothingWasDeleted();
        verifyNoInteractions(minioSpi);
    }

    @Test
    void an_adventure_in_progress_cannot_be_deleted_even_with_the_right_name() {
        ownerIsVerified();
        Adventure running = adventure.withStatus(AdventureStatus.ONGOING);
        when(ownerVerifier.verify(accountId, adventure.getId())).thenReturn(running);

        Assertions.assertThrows(AdventureInProgressException.class,
                () -> service.deleteById(accountId, adventure.getId(), "La quête du soir"));

        nothingWasDeleted();
    }

    @Test
    void whoever_still_listens_to_a_deleted_adventure_is_cut() {
        ownerIsVerified();
        when(plannings.findByAdventureId(adventure.getId())).thenReturn(Optional.empty());

        service.deleteById(accountId, adventure.getId(), "La quête du soir");

        verify(accessRevocation).adventureDeleted(adventure.getId());
    }

    @Test
    void adventures_that_are_not_running_can_be_deleted() {
        for (AdventureStatus status : List.of(AdventureStatus.NOT_STARTED, AdventureStatus.FINISH, AdventureStatus.ABORT)) {
            when(ownerVerifier.verify(accountId, adventure.getId())).thenReturn(adventure.withStatus(status));
            when(plannings.findByAdventureId(adventure.getId())).thenReturn(Optional.empty());

            Assertions.assertDoesNotThrow(() -> service.deleteById(accountId, adventure.getId(), "La quête du soir"), status.name());
        }
    }

    @Test
    void the_name_is_compared_without_the_spaces_around_it() {
        ownerIsVerified();
        when(plannings.findByAdventureId(adventure.getId())).thenReturn(Optional.empty());

        Assertions.assertDoesNotThrow(() -> service.deleteById(accountId, adventure.getId(), "  La quête du soir "));
    }

    // ---- what goes with it ----

    @Test
    void everything_that_belongs_to_the_adventure_is_deleted_and_the_adventure_last() throws Exception {
        ownerIsVerified();
        Planning planning = Planning.builder().adventureId(adventure.getId()).build();
        when(plannings.findByAdventureId(adventure.getId())).thenReturn(Optional.of(planning));
        GameCharacter withPicture = GameCharacter.builder().name("A").picturePath("account/1/a.png").build();
        GameCharacter withoutPicture = GameCharacter.builder().name("B").build();
        when(gameCharacters.findByAdventureId(adventure.getId())).thenReturn(List.of(withPicture, withoutPicture));

        service.deleteById(accountId, adventure.getId(), "La quête du soir");

        InOrder order = inOrder(availabilities, plannings, messages, masterClaims, masterInvitations, spi, minioSpi);
        order.verify(availabilities).deleteByPlanningId(planning.getId());
        order.verify(plannings).deleteById(planning.getId());
        order.verify(messages).deleteByAdventureId(adventure.getId());
        order.verify(masterClaims).deleteByAdventureId(adventure.getId());
        order.verify(masterInvitations).deleteByAdventureId(adventure.getId());
        order.verify(spi).deleteById(adventure.getId());
        order.verify(minioSpi).deleteAfterCommit("account/1/a.png");
        verify(minioSpi, never()).deleteAfterCommit(org.mockito.ArgumentMatchers.eq(null));
        verify(minioSpi, never()).deleteFile(any());
    }

    @Test
    void an_adventure_without_planning_is_deleted_too() {
        ownerIsVerified();
        when(plannings.findByAdventureId(adventure.getId())).thenReturn(Optional.empty());

        service.deleteById(accountId, adventure.getId(), "La quête du soir");

        verify(spi).deleteById(adventure.getId());
        verify(messages).deleteByAdventureId(adventure.getId());
        verify(plannings, never()).deleteById(any());
    }

    @Test
    void the_characters_of_the_players_go_back_to_their_reserve_before_the_adventure_is_deleted() {
        ownerIsVerified();
        when(plannings.findByAdventureId(adventure.getId())).thenReturn(Optional.empty());

        service.deleteById(accountId, adventure.getId(), "La quête du soir");

        InOrder order = inOrder(gameCharacters, spi);
        order.verify(gameCharacters).moveToReserveByAdventureId(adventure.getId());
        order.verify(gameCharacters).findByAdventureId(adventure.getId());
        order.verify(spi).deleteById(adventure.getId());
    }

    @Test
    void the_pictures_of_the_characters_that_moved_to_a_reserve_are_kept() {
        ownerIsVerified();
        when(plannings.findByAdventureId(adventure.getId())).thenReturn(Optional.empty());
        // Once the players' characters have moved, nothing is left in the adventure
        when(gameCharacters.findByAdventureId(adventure.getId())).thenReturn(List.of());

        service.deleteById(accountId, adventure.getId(), "La quête du soir");

        verify(minioSpi, never()).deleteAfterCommit(any());
    }

    @Test
    void an_adventure_that_cannot_be_deleted_is_not_touched_in_its_characters() {
        ownerIsVerified();
        when(ownerVerifier.verify(accountId, adventure.getId())).thenReturn(adventure.withStatus(AdventureStatus.ONGOING));

        Assertions.assertThrows(AdventureInProgressException.class,
                () -> service.deleteById(accountId, adventure.getId(), "La quête du soir"));

        verify(gameCharacters, never()).moveToReserveByAdventureId(any());
    }

    @Test
    void the_pictures_are_kept_when_the_adventure_cannot_be_deleted() throws Exception {
        ownerIsVerified();
        when(plannings.findByAdventureId(adventure.getId())).thenReturn(Optional.empty());
        when(gameCharacters.findByAdventureId(adventure.getId()))
                .thenReturn(List.of(GameCharacter.builder().name("A").picturePath("account/1/a.png").build()));
        doThrow(new IllegalStateException("database refused")).when(spi).deleteById(adventure.getId());

        Assertions.assertThrows(IllegalStateException.class, () -> service.deleteById(accountId, adventure.getId(), "La quête du soir"));

        verify(minioSpi, never()).deleteAfterCommit(any());
        verify(minioSpi, never()).deleteFile(any());
    }
}
