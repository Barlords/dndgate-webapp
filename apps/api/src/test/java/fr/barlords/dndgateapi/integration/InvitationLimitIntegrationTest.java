package fr.barlords.dndgateapi.integration;

import fr.barlords.dndgateapi.bootstrap.DndGateApiApplication;
import fr.barlords.dndgateapi.domain.port.in.adventure.InvitationLogCleanerApi;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import java.util.Map;
import java.util.UUID;

/**
 * Own application context with a low limit per sender (3 per hour). Invitation e-mails are sent in the name of the
 * application: a game master cannot flood a player, nor the mail queue, nor invite someone who cannot join.
 */
@SpringBootTest(
        classes = DndGateApiApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "security.jwt.secret=integration-test-secret-integration-test-secret",
                "management.server.port=0",
                "security.rate-limit.login.max=1000000",
                "security.rate-limit.refresh.max=1000000",
                "security.rate-limit.register.max=1000000",
                "security.rate-limit.verify.max=1000000",
                "security.rate-limit.resend.max=1000000",
                "plans.file=classpath:plans/test-plans.json",
                "invitation.rate-limit.sender.max=3",
                "invitation.rate-limit.sender.window=PT1H",
                "invitation.rate-limit.recipient.window=PT24H"})
@ActiveProfiles("dev")
class InvitationLimitIntegrationTest extends ApiIntegrationTestSupport {

    @Autowired
    InvitationLogCleanerApi cleaner;

    private ResponseEntity<String> invite(TestAccount master, UUID adventureId, TestAccount invited) {
        return call(HttpMethod.POST, "/adventures/" + adventureId + "/invitations/" + invited.pseudo(), master.token(), null);
    }

    private void assertNoMailSent() {
        Mockito.verify(emailingSpi, Mockito.never())
                .sendInvitationCodeEmail(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any());
    }

    private int recorded(TestAccount master) {
        return jdbc.queryForObject("select count(*) from sent_invitations where sender_id = ?", Integer.class, master.id());
    }

    // ---- the adventure and the recipient must be able to use the invitation ----

    @Test
    void an_adventure_that_has_started_sends_no_invitation() {
        TestAccount master = registerAndLogin();
        TestAccount invited = registerAndLogin();
        UUID adventureId = createAdventure(master, "PRIVATE");
        Assertions.assertEquals(200, call(HttpMethod.PUT, "/adventures/" + adventureId + "/status", master.token(),
                Map.of("status", "ONGOING")).getStatusCode().value());

        ResponseEntity<String> refused = invite(master, adventureId, invited);

        Assertions.assertEquals(409, refused.getStatusCode().value());
        Assertions.assertTrue(refused.getHeaders().getContentType().toString().contains("problem+json"));
        assertNoMailSent();
        Assertions.assertEquals(0, recorded(master));
    }

    @Test
    void a_finished_or_aborted_adventure_sends_no_invitation() {
        TestAccount master = registerAndLogin();
        TestAccount invited = registerAndLogin();
        for (String status : new String[]{"FINISH", "ABORT"}) {
            UUID adventureId = createAdventure(master, "PRIVATE");
            if (status.equals("FINISH")) {
                // FINISH is only reachable from ONGOING
                Assertions.assertEquals(200, call(HttpMethod.PUT, "/adventures/" + adventureId + "/status", master.token(),
                        Map.of("status", "ONGOING")).getStatusCode().value());
            }
            Assertions.assertEquals(200, call(HttpMethod.PUT, "/adventures/" + adventureId + "/status", master.token(),
                    Map.of("status", status)).getStatusCode().value());

            Assertions.assertEquals(409, invite(master, adventureId, invited).getStatusCode().value(), status);
        }
        assertNoMailSent();
    }

    @Test
    void a_player_who_already_joined_is_not_invited_again() {
        TestAccount master = registerAndLogin();
        TestAccount player = registerAndLogin();
        UUID adventureId = createAdventure(master, "PUBLIC");
        Assertions.assertEquals(200, call(HttpMethod.POST, "/adventures/" + adventureId + "/join", player.token(), null)
                .getStatusCode().value());

        Assertions.assertEquals(409, invite(master, adventureId, player).getStatusCode().value());
        assertNoMailSent();
        Assertions.assertEquals(0, recorded(master));
    }

    @Test
    void the_game_master_is_not_invited_to_his_own_adventure() {
        TestAccount master = registerAndLogin();
        UUID adventureId = createAdventure(master, "PRIVATE");

        Assertions.assertEquals(409, invite(master, adventureId, master).getStatusCode().value());
        assertNoMailSent();
    }

    // ---- one invitation per recipient and adventure per day ----

    @Test
    void a_recipient_is_invited_once_a_day_to_an_adventure() {
        TestAccount master = registerAndLogin();
        TestAccount invited = registerAndLogin();
        UUID adventureId = createAdventure(master, "PRIVATE");

        Assertions.assertEquals(202, invite(master, adventureId, invited).getStatusCode().value());
        ResponseEntity<String> again = invite(master, adventureId, invited);

        Assertions.assertEquals(429, again.getStatusCode().value());
        long retryAfter = Long.parseLong(again.getHeaders().getFirst("Retry-After"));
        Assertions.assertTrue(retryAfter > 23 * 3600 && retryAfter <= 24 * 3600, "Retry-After " + retryAfter);
        Assertions.assertTrue(again.getHeaders().getContentType().toString().contains("problem+json"));
        Mockito.verify(emailingSpi, Mockito.times(1))
                .sendInvitationCodeEmail(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any());
        Assertions.assertEquals(1, recorded(master));
    }

    @Test
    void the_same_recipient_can_be_invited_to_another_adventure_and_by_another_master() {
        TestAccount master = registerAndLogin();
        TestAccount otherMaster = registerAndLogin();
        TestAccount invited = registerAndLogin();
        UUID first = createAdventure(master, "PRIVATE");
        UUID second = createAdventure(master, "PRIVATE");
        UUID others = createAdventure(otherMaster, "PRIVATE");

        Assertions.assertEquals(202, invite(master, first, invited).getStatusCode().value());
        Assertions.assertEquals(202, invite(master, second, invited).getStatusCode().value());
        Assertions.assertEquals(202, invite(otherMaster, others, invited).getStatusCode().value());
    }

    @Test
    void a_recipient_can_be_invited_again_a_day_later() {
        TestAccount master = registerAndLogin();
        TestAccount invited = registerAndLogin();
        UUID adventureId = createAdventure(master, "PRIVATE");
        Assertions.assertEquals(202, invite(master, adventureId, invited).getStatusCode().value());

        jdbc.update("update sent_invitations set sent_at = now() - interval '25 hours' where sender_id = ?", master.id());

        Assertions.assertEquals(202, invite(master, adventureId, invited).getStatusCode().value());
    }

    // ---- 3 invitations per sender and per hour in this context ----

    @Test
    void a_sender_is_limited_per_hour_whoever_the_recipients_are() {
        TestAccount master = registerAndLogin();
        UUID adventureId = createAdventure(master, "PRIVATE");
        for (int i = 0; i < 3; i++) {
            Assertions.assertEquals(202, invite(master, adventureId, registerAndLogin()).getStatusCode().value());
        }

        ResponseEntity<String> refused = invite(master, adventureId, registerAndLogin());

        Assertions.assertEquals(429, refused.getStatusCode().value());
        long retryAfter = Long.parseLong(refused.getHeaders().getFirst("Retry-After"));
        Assertions.assertTrue(retryAfter > 3500 && retryAfter <= 3600, "Retry-After " + retryAfter);
        Assertions.assertTrue(refused.getBody().contains("Too many invitations"));
        Mockito.verify(emailingSpi, Mockito.times(3))
                .sendInvitationCodeEmail(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any());
    }

    @Test
    void the_limit_of_one_sender_does_not_affect_another() {
        TestAccount busy = registerAndLogin();
        TestAccount other = registerAndLogin();
        UUID busyAdventure = createAdventure(busy, "PRIVATE");
        UUID otherAdventure = createAdventure(other, "PRIVATE");
        for (int i = 0; i < 3; i++) {
            Assertions.assertEquals(202, invite(busy, busyAdventure, registerAndLogin()).getStatusCode().value());
        }

        Assertions.assertEquals(429, invite(busy, busyAdventure, registerAndLogin()).getStatusCode().value());
        Assertions.assertEquals(202, invite(other, otherAdventure, registerAndLogin()).getStatusCode().value());
    }

    @Test
    void a_sender_can_invite_again_once_the_hour_has_passed() {
        TestAccount master = registerAndLogin();
        UUID adventureId = createAdventure(master, "PRIVATE");
        for (int i = 0; i < 3; i++) {
            Assertions.assertEquals(202, invite(master, adventureId, registerAndLogin()).getStatusCode().value());
        }
        Assertions.assertEquals(429, invite(master, adventureId, registerAndLogin()).getStatusCode().value());

        jdbc.update("update sent_invitations set sent_at = now() - interval '61 minutes' where sender_id = ?", master.id());

        Assertions.assertEquals(202, invite(master, adventureId, registerAndLogin()).getStatusCode().value());
    }

    // ---- housekeeping ----

    @Test
    void the_nightly_purge_forgets_invitations_older_than_the_longest_window_only() {
        TestAccount master = registerAndLogin();
        UUID adventureId = createAdventure(master, "PRIVATE");
        Assertions.assertEquals(202, invite(master, adventureId, registerAndLogin()).getStatusCode().value());
        Assertions.assertEquals(202, invite(master, adventureId, registerAndLogin()).getStatusCode().value());
        jdbc.update("update sent_invitations set sent_at = now() - interval '25 hours' where id = "
                + "(select id from sent_invitations where sender_id = ? limit 1)", master.id());

        cleaner.purgeExpired();

        Assertions.assertEquals(1, recorded(master));
    }

    @Test
    void deleting_the_adventure_forgets_its_invitations() {
        TestAccount master = registerAndLogin();
        UUID adventureId = createAdventure(master, "PRIVATE");
        Assertions.assertEquals(202, invite(master, adventureId, registerAndLogin()).getStatusCode().value());

        Assertions.assertEquals(204, call(HttpMethod.DELETE, "/adventures/" + adventureId, master.token(),
                Map.of("confirmation", "Quest")).getStatusCode().value());

        Assertions.assertEquals(0, recorded(master));
    }
}
