package fr.barlords.dndgateapi.domain.functional.service.adventure.master;

import fr.barlords.dndgateapi.domain.functional.model.account.Account;
import fr.barlords.dndgateapi.domain.functional.model.adventure.Adventure;
import fr.barlords.dndgateapi.domain.functional.model.adventure.AdventureStatus;
import fr.barlords.dndgateapi.domain.port.in.account.AccountFinderApi;
import fr.barlords.dndgateapi.domain.port.out.AdventurePersistenceSpi;
import fr.barlords.dndgateapi.domain.port.out.MasterInvitationPersistenceSpi;
import io.vavr.control.Either;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MasterHandOverServiceTest {

    @Mock
    private AdventurePersistenceSpi adventures;

    @Mock
    private MasterInvitationPersistenceSpi invitations;

    @Mock
    private AccountFinderApi accountFinderApi;

    @Mock
    private MasterTransferNotifier notifier;

    private MasterHandOverService service;

    private final UUID formerMaster = UUID.randomUUID();

    private final UUID newMaster = UUID.randomUUID();

    private final UUID other = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new MasterHandOverService(adventures, invitations, accountFinderApi, notifier);
        when(adventures.save(any(Adventure.class))).thenAnswer(i -> Either.right(i.getArguments()[0]));
    }

    private Adventure adventure(int maxPlayer, UUID... players) {
        return Adventure.builder()
                .ownerId(formerMaster)
                .status(AdventureStatus.ONGOING)
                .maxPlayer(maxPlayer)
                .masterVacantSince(Instant.parse("2026-01-01T00:00:00Z"))
                .players(new ArrayList<>(List.of(players)))
                .build();
    }

    private void formerMasterExists(boolean anonymized) {
        Account account = Account.builder().id(formerMaster).anonymizedAt(anonymized ? Instant.now() : null).build();
        when(accountFinderApi.findById(formerMaster)).thenReturn(Optional.of(account));
    }

    @Test
    void the_new_master_owns_the_adventure_and_is_no_longer_a_player() {
        formerMasterExists(false);

        Adventure result = service.handOver(adventure(4, newMaster, other), newMaster);

        Assertions.assertEquals(newMaster, result.getOwnerId());
        Assertions.assertFalse(result.getPlayers().contains(newMaster));
        Assertions.assertFalse(result.isMasterVacant());
        Assertions.assertTrue(result.isMaster(newMaster));
    }

    @Test
    void the_former_master_takes_the_seat_of_the_new_one() {
        formerMasterExists(false);

        Adventure result = service.handOver(adventure(2, newMaster, other), newMaster);

        Assertions.assertEquals(List.of(other, formerMaster), result.getPlayers());
        Assertions.assertTrue(result.isFull());
    }

    @Test
    void the_former_master_leaves_when_there_is_no_seat_for_them() {
        formerMasterExists(false);

        Adventure result = service.handOver(adventure(2, other, UUID.randomUUID()), newMaster);

        Assertions.assertFalse(result.getPlayers().contains(formerMaster));
        Assertions.assertEquals(2, result.getPlayers().size());
    }

    @Test
    void a_deleted_account_does_not_come_back_as_a_player() {
        formerMasterExists(true);

        Adventure result = service.handOver(adventure(4, newMaster, other), newMaster);

        Assertions.assertEquals(List.of(other), result.getPlayers());
    }

    @Test
    void the_people_concerned_are_told_with_the_adventure_as_it_is_after_the_change() {
        formerMasterExists(false);

        Adventure result = service.handOver(adventure(4, newMaster, other), newMaster);

        verify(notifier).masterChanged(result, formerMaster);
    }

    @Test
    void the_pending_invitation_stops_working() {
        formerMasterExists(false);
        Adventure adventure = adventure(4, newMaster);

        service.handOver(adventure, newMaster);

        verify(invitations).deleteByAdventureId(adventure.getId());
    }
}
