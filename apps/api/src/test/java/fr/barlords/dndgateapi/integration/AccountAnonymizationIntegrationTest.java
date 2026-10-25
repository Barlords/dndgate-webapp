package fr.barlords.dndgateapi.integration;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import fr.barlords.dndgateapi.domain.functional.exception.plan.PaymentProviderUnavailableException;
import fr.barlords.dndgateapi.domain.functional.model.plan.BillingInterval;
import fr.barlords.dndgateapi.domain.functional.model.plan.ProviderSubscription;
import fr.barlords.dndgateapi.domain.functional.model.plan.SubscriptionStatus;
import tools.jackson.databind.JsonNode;

import java.util.List;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Deleting an account anonymizes it: the person disappears, what other players rely on stays.
 */
class AccountAnonymizationIntegrationTest extends ApiIntegrationTestSupport {

    private int status(HttpMethod method, String path, TestAccount account, Object body) {
        return call(method, path, account.token(), body).getStatusCode().value();
    }

    private ResponseEntity<String> createCharacter(TestAccount account, UUID adventureId) {
        HttpHeaders pictureHeaders = new HttpHeaders();
        pictureHeaders.setContentType(MediaType.IMAGE_PNG);
        HttpHeaders requestHeaders = new HttpHeaders();
        requestHeaders.setContentType(MediaType.APPLICATION_JSON);

        ByteArrayResource picture = new ByteArrayResource(PNG) {
            @Override
            public String getFilename() {
                return "picture.png";
            }
        };
        LinkedMultiValueMap<String, Object> parts = new LinkedMultiValueMap<>();
        parts.add("picturePresentation", new HttpEntity<>(picture, pictureHeaders));
        parts.add("createGameCharacterRequest", new HttpEntity<>(json.writeValueAsString(Map.of(
                "adventureId", adventureId.toString(), "name", "Gandalf", "race", "Human", "martialClass", "Wizard",
                "personality", "Wise", "background", "Old")), requestHeaders));

        return client().post().uri("/game-characters")
                .headers(headers -> headers.setBearerAuth(account.token()))
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(parts)
                .retrieve().toEntity(String.class);
    }

    private void join(TestAccount account, UUID adventureId) {
        Assertions.assertEquals(200, status(HttpMethod.POST, "/adventures/" + adventureId + "/join", account, null));
    }

    private void setStatus(TestAccount master, UUID adventureId, String adventureStatus) {
        Assertions.assertEquals(200, status(HttpMethod.PUT, "/adventures/" + adventureId + "/status", master,
                Map.of("status", adventureStatus)));
    }

    // ---- the subscription ----

    private ProviderSubscription stripeSubscription(String id, SubscriptionStatus status) {
        return new ProviderSubscription(id, "test-paid", BillingInterval.MONTHLY, status, Instant.now().plusSeconds(3600), false);
    }

    private int subscriptionRows(TestAccount account) {
        return jdbc.queryForObject("select count(*) from subscription where account_id = ?", Integer.class, account.id());
    }

    @Test
    void anonymizing_cancels_the_ongoing_subscriptions_and_forgets_the_row() {
        TestAccount account = registerAndLogin();
        subscribe(account, "test-paid");
        when(paymentProviderSpi.isEnabled()).thenReturn(true);
        when(paymentProviderSpi.findSubscriptions("cus_test_" + account.id())).thenReturn(List.of(
                stripeSubscription("sub_ongoing", SubscriptionStatus.ACTIVE),
                stripeSubscription("sub_over", SubscriptionStatus.CANCELED)));

        Assertions.assertEquals(204, deleteAccount(account));

        verify(paymentProviderSpi).expireOpenCheckoutSessions("cus_test_" + account.id());
        verify(paymentProviderSpi).cancelNow("sub_ongoing");
        verify(paymentProviderSpi, never()).cancelNow("sub_over");
        Assertions.assertEquals(0, subscriptionRows(account));
    }

    @Test
    void an_account_without_customer_is_anonymized_without_stripe() {
        TestAccount account = registerAndLogin();

        Assertions.assertEquals(204, deleteAccount(account));

        verify(paymentProviderSpi, never()).findSubscriptions(any());
        verify(paymentProviderSpi, never()).expireOpenCheckoutSessions(any());
        verify(paymentProviderSpi, never()).cancelNow(any());
    }

    @Test
    void a_stripe_failure_refuses_the_anonymization_and_keeps_the_account() {
        TestAccount account = registerAndLogin();
        subscribe(account, "test-paid");
        when(paymentProviderSpi.isEnabled()).thenReturn(true);
        when(paymentProviderSpi.findSubscriptions("cus_test_" + account.id())).thenReturn(List.of(
                stripeSubscription("sub_ongoing", SubscriptionStatus.ACTIVE)));
        doThrow(new PaymentProviderUnavailableException("Stripe is down")).when(paymentProviderSpi).cancelNow("sub_ongoing");

        Assertions.assertEquals(503, deleteAccount(account));

        Assertions.assertEquals(1, subscriptionRows(account));
        Assertions.assertNull(jdbc.queryForObject("select anonymized_at from accounts where id = ?", java.sql.Timestamp.class, account.id()));
        login(account);
    }

    // ---- the person disappears ----

    @Test
    void deleting_an_account_that_owns_a_character_works() {
        TestAccount master = registerAndLogin();
        TestAccount player = registerAndLogin();
        UUID adventureId = createAdventure(master, "PUBLIC");
        join(player, adventureId);
        Assertions.assertEquals(201, createCharacter(player, adventureId).getStatusCode().value());

        Assertions.assertEquals(204, deleteAccount(player));
    }

    @Test
    void nothing_that_identifies_the_person_stays_in_the_database() {
        TestAccount account = registerAndLogin();
        String oldEmail = account.pseudo() + "@test.fr";

        Assertions.assertEquals(204, deleteAccount(account));

        Map<String, Object> row = jdbc.queryForMap(
                "select pseudo, email, password, activated, anonymized_at from accounts where id = ?", account.id());
        Assertions.assertTrue(row.get("pseudo").toString().startsWith("deleted-"));
        Assertions.assertTrue(row.get("email").toString().endsWith("@anonymized.invalid"));
        Assertions.assertNotEquals(account.password(), row.get("password"));
        Assertions.assertEquals(false, row.get("activated"));
        Assertions.assertNotNull(row.get("anonymized_at"));
        Assertions.assertEquals(0, jdbc.queryForObject("select count(*) from accounts where pseudo = ? or email = ?",
                Integer.class, account.pseudo(), oldEmail));
    }

    @Test
    void the_account_can_no_longer_log_in_or_use_its_tokens() {
        TestAccount account = registerAndLogin();

        Assertions.assertEquals(204, deleteAccount(account));

        Assertions.assertEquals(401, status(HttpMethod.GET, "/accounts/me", account, null));
        Assertions.assertEquals(401, call(HttpMethod.POST, "/accounts/refresh", null,
                Map.of("refreshToken", account.refreshToken())).getStatusCode().value());
        Assertions.assertEquals(401, call(HttpMethod.POST, "/accounts/login", null,
                Map.of("pseudo", account.pseudo(), "password", account.password())).getStatusCode().value());
    }

    @Test
    void deleting_twice_is_refused_because_the_session_is_gone() {
        TestAccount account = registerAndLogin();
        Assertions.assertEquals(204, deleteAccount(account));

        Assertions.assertEquals(401, deleteAccount(account));
    }

    @Test
    void the_pseudo_and_the_email_become_free_again() {
        TestAccount account = registerAndLogin();
        deleteAccount(account);

        Assertions.assertEquals(202, call(HttpMethod.POST, "/accounts", null,
                Map.of("email", account.pseudo() + "@test.fr", "password", "password123", "pseudo", account.pseudo()))
                .getStatusCode().value());

        // Signup always answers 202: only the database tells that the address and the pseudo were free (a pending
        // signup was recorded, no account exists yet)
        Assertions.assertEquals(1, jdbc.queryForObject("select count(*) from pending_signups where pseudo = ?", Integer.class, account.pseudo()));
        Assertions.assertEquals(1, jdbc.queryForObject("select count(*) from pending_signups where email = ?", Integer.class, account.pseudo() + "@test.fr"));
        Assertions.assertEquals(0, jdbc.queryForObject("select count(*) from accounts where pseudo = ?", Integer.class, account.pseudo()));
    }

    @Test
    void the_old_pseudo_cannot_be_looked_up_any_more() {
        TestAccount account = registerAndLogin();
        TestAccount other = registerAndLogin();
        deleteAccount(account);

        Assertions.assertEquals(404, status(HttpMethod.GET, "/accounts/pseudo/" + account.pseudo(), other, null));
    }

    @Test
    void the_anonymous_account_is_flagged_so_that_pages_can_say_deleted_account() {
        TestAccount account = registerAndLogin();
        TestAccount other = registerAndLogin();
        deleteAccount(account);

        JsonNode lookup = body(call(HttpMethod.GET, "/accounts/id/" + account.id(), other.token(), null));

        Assertions.assertTrue(lookup.get("anonymized").asBoolean());
        Assertions.assertTrue(lookup.get("pseudo").asString().startsWith("deleted-"));
        Assertions.assertFalse(lookup.has("email"));
        Assertions.assertFalse(body(call(HttpMethod.GET, "/accounts/id/" + other.id(), other.token(), null)).get("anonymized").asBoolean());
    }

    @Test
    void an_anonymous_account_cannot_be_verified_and_that_is_a_plain_refusal() {
        TestAccount account = registerAndLogin();
        String anonymousEmail = account.id().toString().replace("-", "") + "@anonymized.invalid";
        deleteAccount(account);

        Assertions.assertEquals(400, verifyEmail(anonymousEmail, "anything"));
        Assertions.assertEquals(false, jdbc.queryForObject("select activated from accounts where id = ?", Boolean.class, account.id()));
    }

    @Test
    void an_anonymous_account_cannot_be_invited_and_no_mail_is_sent_to_its_fake_address() {
        TestAccount master = registerAndLogin();
        TestAccount deleted = registerAndLogin();
        UUID adventureId = createAdventure(master, "PUBLIC");
        deleteAccount(deleted);
        String anonymousPseudo = jdbc.queryForObject("select pseudo from accounts where id = ?", String.class, deleted.id());

        Assertions.assertEquals(404, status(HttpMethod.POST,
                "/adventures/" + adventureId + "/invitations/" + anonymousPseudo, master, null));
        org.mockito.Mockito.verify(emailingSpi, org.mockito.Mockito.never())
                .sendInvitationCodeEmail(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    // ---- what other players rely on stays ----

    @Test
    void the_characters_of_the_deleted_account_stay_readable_by_the_other_participants() {
        TestAccount master = registerAndLogin();
        TestAccount player = registerAndLogin();
        UUID adventureId = createAdventure(master, "PUBLIC");
        join(player, adventureId);
        UUID characterId = UUID.fromString(body(createCharacter(player, adventureId)).get("id").asString());

        deleteAccount(player);

        ResponseEntity<String> character = call(HttpMethod.GET, "/game-characters/" + characterId, master.token(), null);
        Assertions.assertEquals(200, character.getStatusCode().value());
        Assertions.assertEquals("Gandalf", body(character).get("name").asString());
        Assertions.assertEquals(player.id().toString(), body(character).get("ownerId").asString());
        Assertions.assertTrue(call(HttpMethod.GET, "/game-characters?adventureId=" + adventureId, master.token(), null)
                .getBody().contains("Gandalf"));
    }

    @Test
    void messages_and_votes_stay_in_the_history_of_the_adventure() {
        TestAccount master = registerAndLogin();
        TestAccount player = registerAndLogin();
        UUID adventureId = createAdventure(master, "PUBLIC");
        join(player, adventureId);
        call(HttpMethod.POST, "/messages", player.token(), Map.of("adventureId", adventureId.toString(),
                "content", "hello table"));

        deleteAccount(player);

        String history = call(HttpMethod.GET, "/messages?adventureId=" + adventureId, master.token(), null).getBody();
        Assertions.assertTrue(history.contains("hello table"));
        Assertions.assertTrue(history.contains(player.id().toString()));
    }

    @Test
    void the_personal_spell_books_are_deleted() {
        TestAccount account = registerAndLogin();
        call(HttpMethod.POST, "/spellbooks", account.token(), Map.of("name", "My book", "description", "Fireballs", "ruleset", "dnd5e-2014"));
        Assertions.assertEquals(1, jdbc.queryForObject("select count(*) from spellbook where account_id = ?", Integer.class, account.id()));

        deleteAccount(account);

        Assertions.assertEquals(0, jdbc.queryForObject("select count(*) from spellbook where account_id = ?", Integer.class, account.id()));
    }

    // ---- adventures ----

    @Test
    void an_adventure_that_has_not_started_is_aborted_and_no_longer_listed() {
        TestAccount master = registerAndLogin();
        TestAccount other = registerAndLogin();
        UUID adventureId = createAdventure(master, "PUBLIC");
        // the list is paginated and shared with other tests: what is checked is that the total goes down by one
        long listedBefore = body(call(HttpMethod.GET, "/adventures", other.token(), null)).get("totalElements").asLong();
        Assertions.assertEquals(jdbc.queryForObject("select count(*) from adventure where visibility = 'PUBLIC'", Long.class), listedBefore);

        deleteAccount(master);

        Assertions.assertEquals(listedBefore - 1, body(call(HttpMethod.GET, "/adventures", other.token(), null)).get("totalElements").asLong());
        Assertions.assertEquals(404, status(HttpMethod.POST, "/adventures/" + adventureId + "/join", other, null));
        Assertions.assertEquals("ABORT", jdbc.queryForObject("select status from adventure where id = ?", String.class, adventureId));
        Assertions.assertEquals("PRIVATE", jdbc.queryForObject("select visibility from adventure where id = ?", String.class, adventureId));
    }

    @Test
    void a_running_adventure_stays_available_to_its_players() {
        TestAccount master = registerAndLogin();
        TestAccount player = registerAndLogin();
        UUID adventureId = createAdventure(master, "PUBLIC");
        join(player, adventureId);
        setStatus(master, adventureId, "ONGOING");
        Assertions.assertTrue(createCharacter(player, adventureId).getStatusCode().is2xxSuccessful());

        deleteAccount(master);

        Assertions.assertEquals(200, status(HttpMethod.GET, "/adventures/" + adventureId, player, null));
        Assertions.assertEquals(200, status(HttpMethod.GET, "/game-characters?adventureId=" + adventureId, player, null));
        Assertions.assertEquals("ONGOING", jdbc.queryForObject("select status from adventure where id = ?", String.class, adventureId));
    }

    @Test
    void the_seat_is_freed_in_an_adventure_that_has_not_started_but_kept_in_a_running_one() {
        TestAccount master = registerAndLogin();
        TestAccount leaving = registerAndLogin();
        UUID open = createAdventure(master, "PUBLIC");
        UUID running = createAdventure(master, "PUBLIC");
        join(leaving, open);
        join(leaving, running);
        setStatus(master, running, "ONGOING");

        deleteAccount(leaving);

        Assertions.assertEquals(0, jdbc.queryForObject("select count(*) from adventure_players where adventure_id = ? and player_id = ?",
                Integer.class, open, leaving.id()));
        Assertions.assertEquals(1, jdbc.queryForObject("select count(*) from adventure_players where adventure_id = ? and player_id = ?",
                Integer.class, running, leaving.id()));
    }

    @Test
    void ratings_given_and_received_stay() {
        TestAccount master = registerAndLogin();
        TestAccount player = registerAndLogin();
        UUID adventureId = createAdventure(master, "PUBLIC");
        join(player, adventureId);
        setStatus(master, adventureId, "ONGOING");
        Assertions.assertEquals(201, status(HttpMethod.POST, "/ratings", player,
                Map.of("accountTargetedId", master.id().toString(), "rate", 4)));

        deleteAccount(player);

        Assertions.assertEquals("{\"average\":4.0,\"count\":1}", call(HttpMethod.GET, "/ratings/mean?accountId=" + master.id(),
                master.token(), null).getBody());
    }
}
