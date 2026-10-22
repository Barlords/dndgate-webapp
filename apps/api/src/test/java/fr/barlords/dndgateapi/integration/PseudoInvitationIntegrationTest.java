package fr.barlords.dndgateapi.integration;

import fr.barlords.dndgateapi.domain.functional.model.account.Account;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

import java.util.Map;
import java.util.UUID;

/**
 * Accounts are found by pseudo only: an e-mail address is never a way to look someone up, and a pseudo
 * designates exactly one account (whatever its case).
 */
class PseudoInvitationIntegrationTest extends ApiIntegrationTestSupport {

    private int status(HttpMethod method, String path, TestAccount account, Object body) {
        return call(method, path, account.token(), body).getStatusCode().value();
    }

    private int register(String pseudo, String email) {
        return call(HttpMethod.POST, "/accounts", null,
                Map.of("email", email, "password", "password-123", "pseudo", pseudo)).getStatusCode().value();
    }

    private String invitePath(UUID adventureId, String pseudo) {
        return "/adventures/" + adventureId + "/invitations/" + pseudo;
    }

    // ---- no lookup by e-mail address ----

    @Test
    void an_account_cannot_be_looked_up_by_e_mail_address() {
        TestAccount asker = registerAndLogin();
        TestAccount target = registerAndLogin();

        ResponseEntity<String> response = call(HttpMethod.GET, "/accounts/email/" + target.pseudo() + "@test.fr", asker.token(), null);

        Assertions.assertTrue(response.getStatusCode().is4xxClientError(), String.valueOf(response.getStatusCode()));
        Assertions.assertFalse(String.valueOf(response.getBody()).contains(target.id().toString()));
    }

    // ---- invitation by pseudo ----

    @Test
    void the_game_master_invites_a_player_by_pseudo() {
        TestAccount master = registerAndLogin();
        TestAccount invited = registerAndLogin();
        UUID adventureId = createAdventure(master, "PRIVATE");

        Assertions.assertEquals(202, status(HttpMethod.POST, invitePath(adventureId, invited.pseudo()), master, null));

        ArgumentCaptor<Account> receiver = ArgumentCaptor.forClass(Account.class);
        Mockito.verify(emailingSpi).sendInvitationCodeEmail(ArgumentMatchers.any(), receiver.capture(), ArgumentMatchers.any());
        Assertions.assertEquals(invited.id(), receiver.getValue().getId());
    }

    @Test
    void the_pseudo_of_an_invitation_is_read_whatever_its_case() {
        TestAccount master = registerAndLogin();
        TestAccount invited = registerAndLogin();
        UUID adventureId = createAdventure(master, "PRIVATE");

        Assertions.assertEquals(202, status(HttpMethod.POST, invitePath(adventureId, invited.pseudo().toUpperCase()), master, null));
    }

    @Test
    void an_unknown_pseudo_cannot_be_invited_and_no_mail_is_sent() {
        TestAccount master = registerAndLogin();
        UUID adventureId = createAdventure(master, "PRIVATE");

        Assertions.assertEquals(404, status(HttpMethod.POST, invitePath(adventureId, "nobody-" + UUID.randomUUID().toString().substring(0, 8)), master, null));
        Mockito.verify(emailingSpi, Mockito.never())
                .sendInvitationCodeEmail(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any());
    }

    @Test
    void an_account_id_is_not_a_way_to_invite_any_more() {
        TestAccount master = registerAndLogin();
        TestAccount invited = registerAndLogin();
        UUID adventureId = createAdventure(master, "PRIVATE");

        Assertions.assertEquals(404, status(HttpMethod.POST, invitePath(adventureId, invited.id().toString()), master, null));
        Mockito.verify(emailingSpi, Mockito.never())
                .sendInvitationCodeEmail(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any());
    }

    @Test
    void only_the_game_master_can_invite() {
        TestAccount master = registerAndLogin();
        TestAccount stranger = registerAndLogin();
        TestAccount invited = registerAndLogin();
        UUID adventureId = createAdventure(master, "PRIVATE");

        Assertions.assertEquals(403, status(HttpMethod.POST, invitePath(adventureId, invited.pseudo()), stranger, null));
        Mockito.verify(emailingSpi, Mockito.never())
                .sendInvitationCodeEmail(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any());
    }

    // ---- a pseudo designates one account ----

    @Test
    void a_pseudo_differing_only_by_case_is_already_taken() {
        TestAccount existing = registerAndLogin();

        Assertions.assertEquals(409, register(existing.pseudo().toUpperCase(), "other-" + UUID.randomUUID() + "@test.fr"));
    }

    @Test
    void the_database_itself_refuses_two_pseudos_differing_only_by_case() {
        TestAccount existing = registerAndLogin();
        TestAccount other = registerAndLogin();

        Assertions.assertThrows(org.springframework.dao.DuplicateKeyException.class, () -> jdbc.update(
                "update accounts set pseudo = ? where id = ?", existing.pseudo().toUpperCase(), other.id()));
    }

    @Test
    void an_account_can_change_only_the_case_of_its_own_pseudo() {
        TestAccount account = registerAndLogin();
        String recased = account.pseudo().toUpperCase();

        Assertions.assertEquals(200, status(HttpMethod.PATCH, "/accounts", account,
                Map.of("currentPassword", account.password(), "pseudo", recased)));
        Assertions.assertEquals(recased, jdbc.queryForObject("select pseudo from accounts where id = ?", String.class, account.id()));
    }

    @Test
    void logging_in_ignores_the_case_of_the_pseudo() {
        TestAccount account = registerAndLogin();

        Assertions.assertEquals(200, call(HttpMethod.POST, "/accounts/login", null,
                Map.of("pseudo", account.pseudo().toUpperCase(), "password", account.password())).getStatusCode().value());
    }
}
