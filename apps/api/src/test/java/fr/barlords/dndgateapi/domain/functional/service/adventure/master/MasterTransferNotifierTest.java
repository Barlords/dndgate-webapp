package fr.barlords.dndgateapi.domain.functional.service.adventure.master;

import fr.barlords.dndgateapi.domain.functional.model.account.Account;
import fr.barlords.dndgateapi.domain.functional.model.adventure.Adventure;
import fr.barlords.dndgateapi.domain.functional.model.adventure.master.MasterTransferEvent;
import fr.barlords.dndgateapi.domain.functional.model.adventure.master.MasterTransferMessage;
import fr.barlords.dndgateapi.domain.port.in.account.AccountFinderApi;
import fr.barlords.dndgateapi.domain.port.out.EmailingSpi;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MasterTransferNotifierTest {

    @Mock
    private AccountFinderApi accountFinderApi;

    @Mock
    private EmailingSpi emailingSpi;

    private MasterTransferNotifier notifier;

    private final Account gandalf = Account.builder().pseudo("Gandalf").email("gandalf@middle.earth").build();

    private final Account frodo = Account.builder().pseudo("Frodo").email("frodo@middle.earth").build();

    private final Account sam = Account.builder().pseudo("Sam").email("sam@middle.earth").build();

    private final Account pippin = Account.builder().pseudo("Pippin").email("pippin@middle.earth").build();

    private final Account deleted = Account.builder().pseudo("deleted-1").email("x@anonymized.invalid").anonymizedAt(Instant.now()).build();

    @BeforeEach
    void setUp() {
        notifier = new MasterTransferNotifier(accountFinderApi, emailingSpi);
        for (Account account : List.of(gandalf, frodo, sam, pippin, deleted)) {
            org.mockito.Mockito.lenient().when(accountFinderApi.findById(account.getId())).thenReturn(Optional.of(account));
        }
    }

    private Adventure adventure(Account master, Account... players) {
        return Adventure.builder().ownerId(master.getId()).name("The lost mine")
                .players(new ArrayList<>(java.util.Arrays.stream(players).map(Account::getId).toList())).build();
    }

    /** Receiver -> what they were told. */
    private Map<UUID, MasterTransferMessage> sent() {
        ArgumentCaptor<Account> receivers = ArgumentCaptor.forClass(Account.class);
        ArgumentCaptor<MasterTransferMessage> messages = ArgumentCaptor.forClass(MasterTransferMessage.class);
        verify(emailingSpi, atLeast(0)).sendMasterTransferEmail(receivers.capture(), messages.capture());
        Map<UUID, MasterTransferMessage> result = new HashMap<>();
        for (int i = 0; i < receivers.getAllValues().size(); i++) {
            Assertions.assertNull(result.put(receivers.getAllValues().get(i).getId(), messages.getAllValues().get(i)), "nobody is told twice");
        }
        return result;
    }

    @Test
    void when_the_master_steps_down_the_players_are_told_by_whom() {
        notifier.masterSteppedDown(adventure(gandalf, frodo, sam));

        Map<UUID, MasterTransferMessage> sent = sent();
        Assertions.assertEquals(2, sent.size());
        Assertions.assertEquals(MasterTransferEvent.MASTER_STEPPED_DOWN, sent.get(frodo.getId()).event());
        Assertions.assertEquals("Gandalf", sent.get(frodo.getId()).otherPseudo());
        Assertions.assertEquals("The lost mine", sent.get(sam.getId()).adventureName());
        Assertions.assertFalse(sent.containsKey(gandalf.getId()));
    }

    @Test
    void when_the_account_of_the_master_is_deleted_nothing_is_said_about_who_it_was() {
        notifier.masterAccountDeleted(adventure(deleted, frodo, sam, deleted));

        Map<UUID, MasterTransferMessage> sent = sent();
        Assertions.assertEquals(2, sent.size());
        Assertions.assertEquals(MasterTransferEvent.MASTER_ACCOUNT_DELETED, sent.get(frodo.getId()).event());
        Assertions.assertNull(sent.get(frodo.getId()).otherPseudo());
    }

    @Test
    void the_players_who_vote_are_told_who_asks() {
        notifier.claimOpened(adventure(gandalf, frodo, sam, pippin), frodo.getId(), List.of(sam.getId(), pippin.getId()));

        Map<UUID, MasterTransferMessage> sent = sent();
        Assertions.assertEquals(2, sent.size());
        Assertions.assertEquals(MasterTransferEvent.CLAIM_OPENED, sent.get(sam.getId()).event());
        Assertions.assertEquals("Frodo", sent.get(pippin.getId()).otherPseudo());
    }

    @Test
    void the_candidate_is_told_about_a_refusal_and_the_voters_about_a_withdrawal() {
        Adventure adventure = adventure(gandalf, frodo, sam);

        notifier.claimRejected(adventure, frodo.getId());
        notifier.claimCancelled(adventure, frodo.getId(), List.of(sam.getId()));

        ArgumentCaptor<Account> receivers = ArgumentCaptor.forClass(Account.class);
        ArgumentCaptor<MasterTransferMessage> messages = ArgumentCaptor.forClass(MasterTransferMessage.class);
        verify(emailingSpi, org.mockito.Mockito.times(2)).sendMasterTransferEmail(receivers.capture(), messages.capture());
        Assertions.assertEquals(frodo.getId(), receivers.getAllValues().get(0).getId());
        Assertions.assertEquals(MasterTransferEvent.CLAIM_REJECTED, messages.getAllValues().get(0).event());
        Assertions.assertEquals(sam.getId(), receivers.getAllValues().get(1).getId());
        Assertions.assertEquals(MasterTransferEvent.CLAIM_CANCELLED, messages.getAllValues().get(1).event());
    }

    @Test
    void when_the_role_changes_hands_each_one_is_told_what_concerns_them() {
        // after the change: Frodo is master, Gandalf (former master) took a seat, Sam is a player
        notifier.masterChanged(adventure(frodo, sam, gandalf), gandalf.getId());

        Map<UUID, MasterTransferMessage> sent = sent();
        Assertions.assertEquals(3, sent.size());
        Assertions.assertEquals(MasterTransferEvent.YOU_ARE_NOW_MASTER, sent.get(frodo.getId()).event());
        Assertions.assertEquals(MasterTransferEvent.YOU_ARE_NO_LONGER_MASTER, sent.get(gandalf.getId()).event());
        Assertions.assertEquals("Frodo", sent.get(gandalf.getId()).otherPseudo());
        Assertions.assertEquals(MasterTransferEvent.MASTER_CHANGED, sent.get(sam.getId()).event());
        Assertions.assertEquals("Frodo", sent.get(sam.getId()).otherPseudo());
    }

    @Test
    void a_deleted_former_master_is_not_written_to() {
        notifier.masterChanged(adventure(frodo, sam), deleted.getId());

        Map<UUID, MasterTransferMessage> sent = sent();
        Assertions.assertEquals(2, sent.size());
        Assertions.assertFalse(sent.containsKey(deleted.getId()));
    }

    @Test
    void a_new_master_who_was_already_the_owner_is_told_once() {
        notifier.masterChanged(adventure(frodo, sam), frodo.getId());

        Map<UUID, MasterTransferMessage> sent = sent();
        Assertions.assertEquals(MasterTransferEvent.YOU_ARE_NOW_MASTER, sent.get(frodo.getId()).event());
        Assertions.assertEquals(2, sent.size());
    }

    @Test
    void nobody_is_told_when_there_is_nobody_to_tell() {
        notifier.claimOpened(adventure(gandalf, frodo), frodo.getId(), List.of());

        verify(emailingSpi, never()).sendMasterTransferEmail(any(), any());
    }
}
