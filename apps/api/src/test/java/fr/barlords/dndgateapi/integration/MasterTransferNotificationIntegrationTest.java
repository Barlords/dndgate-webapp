package fr.barlords.dndgateapi.integration;

import fr.barlords.dndgateapi.domain.functional.model.account.Account;
import fr.barlords.dndgateapi.domain.functional.model.adventure.master.MasterTransferEvent;
import fr.barlords.dndgateapi.domain.functional.model.adventure.master.MasterTransferMessage;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/** The people concerned are told by e-mail (the mailing service is replaced by a mock that records what it is asked to send). */
class MasterTransferNotificationIntegrationTest extends ApiIntegrationTestSupport {

    private TestAccount master;

    private TestAccount first;

    private TestAccount second;

    private UUID adventureId;

    private String adventurePath;

    @BeforeEach
    void fixture() {
        master = registerAndLogin();
        first = registerAndLogin();
        second = registerAndLogin();
        adventureId = createAdventure(master, "PUBLIC");
        adventurePath = "/adventures/" + adventureId;
        for (TestAccount player : List.of(first, second)) {
            Assertions.assertEquals(200, call(HttpMethod.POST, adventurePath + "/join", player.token(), null).getStatusCode().value());
        }
        jdbc.update("update adventure set status = 'ONGOING' where id = ?", adventureId);
        clearInvocations(emailingSpi);
    }

    /** Who was told what since the start of the test. */
    private Map<UUID, List<MasterTransferEvent>> told() {
        ArgumentCaptor<Account> receivers = ArgumentCaptor.forClass(Account.class);
        ArgumentCaptor<MasterTransferMessage> messages = ArgumentCaptor.forClass(MasterTransferMessage.class);
        verify(emailingSpi, atLeast(0)).sendMasterTransferEmail(receivers.capture(), messages.capture());

        Map<UUID, List<MasterTransferEvent>> result = new HashMap<>();
        for (int i = 0; i < receivers.getAllValues().size(); i++) {
            Assertions.assertEquals(adventureId, messages.getAllValues().get(i).adventureId());
            result.computeIfAbsent(receivers.getAllValues().get(i).getId(), id -> new java.util.ArrayList<>())
                    .add(messages.getAllValues().get(i).event());
        }
        return result;
    }

    private void stepDown() {
        Assertions.assertEquals(200, call(HttpMethod.POST, adventurePath + "/master/step-down", master.token(), Map.of("confirmation", "Quest")).getStatusCode().value());
    }

    private ResponseEntity<String> claim(TestAccount account) {
        return call(HttpMethod.POST, adventurePath + "/master/claim", account.token(), null);
    }

    private ResponseEntity<String> vote(TestAccount account, boolean approve) {
        return call(HttpMethod.PUT, adventurePath + "/master/claim/vote", account.token(), Map.of("approve", approve));
    }

    @Test
    void stepping_down_tells_the_players_and_not_the_master() {
        stepDown();

        Assertions.assertEquals(Map.of(
                first.id(), List.of(MasterTransferEvent.MASTER_STEPPED_DOWN),
                second.id(), List.of(MasterTransferEvent.MASTER_STEPPED_DOWN)), told());
    }

    @Test
    void asking_for_the_role_tells_the_players_who_vote() {
        stepDown();
        clearInvocations(emailingSpi);

        claim(first);

        Assertions.assertEquals(Map.of(second.id(), List.of(MasterTransferEvent.CLAIM_OPENED)), told());
    }

    @Test
    void a_refusal_tells_the_candidate() {
        stepDown();
        claim(first);
        clearInvocations(emailingSpi);

        vote(second, false);

        Assertions.assertEquals(Map.of(first.id(), List.of(MasterTransferEvent.CLAIM_REJECTED)), told());
    }

    @Test
    void giving_up_tells_the_voters() {
        stepDown();
        claim(first);
        clearInvocations(emailingSpi);

        call(HttpMethod.DELETE, adventurePath + "/master/claim", first.token(), null);

        Assertions.assertEquals(Map.of(second.id(), List.of(MasterTransferEvent.CLAIM_CANCELLED)), told());
    }

    @Test
    void a_vote_that_succeeds_tells_the_new_master_the_former_one_and_the_others() {
        stepDown();
        claim(first);
        clearInvocations(emailingSpi);

        vote(second, true);

        Assertions.assertEquals(Map.of(
                first.id(), List.of(MasterTransferEvent.YOU_ARE_NOW_MASTER),
                master.id(), List.of(MasterTransferEvent.YOU_ARE_NO_LONGER_MASTER),
                second.id(), List.of(MasterTransferEvent.MASTER_CHANGED)), told());
    }

    @Test
    void an_invitation_that_is_accepted_tells_everybody() {
        TestAccount newcomer = registerAndLogin();
        String code = body(call(HttpMethod.POST, adventurePath + "/master/invitation", master.token(), null)).get("code").asString();
        clearInvocations(emailingSpi);

        call(HttpMethod.POST, "/adventures/master/accept", newcomer.token(), Map.of("code", code));

        Map<UUID, List<MasterTransferEvent>> told = told();
        Assertions.assertEquals(List.of(MasterTransferEvent.YOU_ARE_NOW_MASTER), told.get(newcomer.id()));
        Assertions.assertEquals(List.of(MasterTransferEvent.YOU_ARE_NO_LONGER_MASTER), told.get(master.id()));
        Assertions.assertEquals(List.of(MasterTransferEvent.MASTER_CHANGED), told.get(first.id()));
        Assertions.assertEquals(List.of(MasterTransferEvent.MASTER_CHANGED), told.get(second.id()));
    }

    @Test
    void when_the_master_deletes_the_account_the_players_are_told_and_the_former_master_is_not() {
        deleteAccount(master);

        Assertions.assertEquals(Map.of(
                first.id(), List.of(MasterTransferEvent.MASTER_ACCOUNT_DELETED),
                second.id(), List.of(MasterTransferEvent.MASTER_ACCOUNT_DELETED)), told());
    }

    @Test
    void when_the_candidate_deletes_the_account_the_voters_are_told_the_vote_is_cancelled() {
        stepDown();
        claim(first);
        clearInvocations(emailingSpi);

        deleteAccount(first);

        Assertions.assertEquals(Map.of(second.id(), List.of(MasterTransferEvent.CLAIM_CANCELLED)), told());
    }

    @Test
    void a_refused_request_tells_nobody() {
        claim(first);
        vote(second, true);
        call(HttpMethod.POST, "/adventures/master/accept", first.token(), Map.of("code", "unknown"));
        call(HttpMethod.POST, adventurePath + "/master/step-down", first.token(), Map.of("confirmation", "Quest"));
        call(HttpMethod.POST, adventurePath + "/master/step-down", master.token(), Map.of("confirmation", "wrong"));

        verify(emailingSpi, never()).sendMasterTransferEmail(any(), any());
    }
}
