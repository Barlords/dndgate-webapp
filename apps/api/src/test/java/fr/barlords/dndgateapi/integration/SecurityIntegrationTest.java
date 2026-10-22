package fr.barlords.dndgateapi.integration;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import tools.jackson.databind.JsonNode;

import java.util.Map;
import java.util.UUID;

class SecurityIntegrationTest extends ApiIntegrationTestSupport {

    // ---- Authentication ----

    @Test
    void requests_without_token_are_rejected() {
        Assertions.assertEquals(401, call(HttpMethod.GET, "/adventures", null, null).getStatusCode().value());
        Assertions.assertEquals(401, call(HttpMethod.GET, "/accounts/me", null, null).getStatusCode().value());
        Assertions.assertEquals(401, call(HttpMethod.POST, "/webrtc/offer", null, Map.of()).getStatusCode().value());
    }

    @Test
    void forged_or_garbage_tokens_are_rejected() {
        Assertions.assertEquals(401, call(HttpMethod.GET, "/accounts/me", "not-a-jwt", null).getStatusCode().value());
        // a well formed UUID used to be a valid token with the previous opaque token scheme
        Assertions.assertEquals(401, call(HttpMethod.GET, "/accounts/me", UUID.randomUUID().toString(), null).getStatusCode().value());
    }

    @Test
    void login_is_refused_until_the_account_is_activated() {
        String pseudo = "inactive" + UUID.randomUUID().toString().substring(0, 6);
        call(HttpMethod.POST, "/accounts", null, Map.of("email", pseudo + "@test.fr", "password", "password123", "pseudo", pseudo));

        Assertions.assertEquals(401,
                call(HttpMethod.POST, "/accounts/login", null, Map.of("pseudo", pseudo, "password", "password123")).getStatusCode().value());
    }

    @Test
    void wrong_password_is_refused() {
        TestAccount account = registerAndLogin();

        Assertions.assertEquals(401,
                call(HttpMethod.POST, "/accounts/login", null, Map.of("pseudo", account.pseudo(), "password", "wrong-password")).getStatusCode().value());
    }

    @Test
    void passwords_are_stored_hashed_with_bcrypt() {
        TestAccount account = registerAndLogin();

        String stored = jdbc.queryForObject("select password from accounts where pseudo = ?", String.class, account.pseudo());

        Assertions.assertNotEquals(account.password(), stored);
        Assertions.assertTrue(stored.startsWith("$2"), "expected a bcrypt hash but got " + stored);
    }

    @Test
    void a_valid_token_gives_access_to_the_own_profile() {
        TestAccount account = registerAndLogin();

        ResponseEntity<String> me = call(HttpMethod.GET, "/accounts/me", account.token(), null);

        Assertions.assertEquals(200, me.getStatusCode().value());
        Assertions.assertEquals(account.pseudo(), body(me).get("pseudo").asString());
    }

    @Test
    void a_token_of_a_deleted_account_is_rejected() {
        TestAccount account = registerAndLogin();

        Assertions.assertEquals(204, deleteAccount(account));

        Assertions.assertEquals(401, call(HttpMethod.GET, "/accounts/me", account.token(), null).getStatusCode().value());
    }

    @Test
    void server_errors_are_not_masked_as_authentication_failures() {
        TestAccount account = registerAndLogin();

        // a malformed JSON body is an error raised outside of any controller advice
        ResponseEntity<String> response = client().post().uri("/adventures")
                .headers(headers -> headers.setBearerAuth(account.token()))
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .body("{not json")
                .retrieve().toEntity(String.class);

        Assertions.assertEquals(400, response.getStatusCode().value());
    }

    // ---- Personal data exposure ----

    @Test
    void public_account_lookups_do_not_expose_the_email() {
        TestAccount alice = registerAndLogin();
        TestAccount bob = registerAndLogin();

        JsonNode byId = body(call(HttpMethod.GET, "/accounts/id/" + alice.id(), bob.token(), null));
        JsonNode byPseudo = body(call(HttpMethod.GET, "/accounts/pseudo/" + alice.pseudo(), bob.token(), null));

        Assertions.assertEquals(alice.pseudo(), byId.get("pseudo").asString());
        Assertions.assertFalse(byId.has("email"));
        Assertions.assertFalse(byPseudo.has("email"));
    }

    @Test
    void removed_account_endpoints_are_not_reachable() {
        TestAccount account = registerAndLogin();

        // list of every account (with their emails) and "create or overwrite account by id"
        Assertions.assertTrue(call(HttpMethod.GET, "/accounts", account.token(), null).getStatusCode().is4xxClientError());
        Assertions.assertTrue(call(HttpMethod.PUT, "/accounts/" + account.id(), account.token(),
                Map.of("email", "evil@test.fr", "password", "password123", "pseudo", "evil")).getStatusCode().is4xxClientError());
    }

    // ---- Adventure authorization ----

    @Test
    void only_the_owner_can_delete_an_adventure() {
        TestAccount owner = registerAndLogin();
        TestAccount stranger = registerAndLogin();
        UUID adventureId = createAdventure(owner, "PUBLIC");

        Assertions.assertEquals(403, call(HttpMethod.DELETE, "/adventures/" + adventureId, stranger.token(), Map.of("confirmation", "Quest")).getStatusCode().value());
        Assertions.assertEquals(200, call(HttpMethod.GET, "/adventures/" + adventureId, owner.token(), null).getStatusCode().value());

        Assertions.assertEquals(204, call(HttpMethod.DELETE, "/adventures/" + adventureId, owner.token(), Map.of("confirmation", "Quest")).getStatusCode().value());
        Assertions.assertEquals(404, call(HttpMethod.GET, "/adventures/" + adventureId, owner.token(), null).getStatusCode().value());
    }

    @Test
    void private_adventures_are_hidden_from_strangers() {
        TestAccount owner = registerAndLogin();
        TestAccount stranger = registerAndLogin();
        UUID privateId = createAdventure(owner, "PRIVATE");
        UUID publicId = createAdventure(owner, "PUBLIC");

        Assertions.assertEquals(404, call(HttpMethod.GET, "/adventures/" + privateId, stranger.token(), null).getStatusCode().value());
        Assertions.assertEquals(200, call(HttpMethod.GET, "/adventures/" + publicId, stranger.token(), null).getStatusCode().value());

        // the list is paginated and shared with other tests: the stranger's total is exactly the public adventures
        JsonNode strangerList = body(call(HttpMethod.GET, "/adventures?size=100", stranger.token(), null));
        Assertions.assertFalse(strangerList.toString().contains(privateId.toString()));
        Assertions.assertEquals(jdbc.queryForObject("select count(*) from adventure where visibility = 'PUBLIC'", Long.class),
                strangerList.get("totalElements").asLong());

        String ownerList = call(HttpMethod.GET, "/adventures/as-master", owner.token(), null).getBody();
        Assertions.assertTrue(ownerList.contains(privateId.toString()));
        Assertions.assertTrue(ownerList.contains(publicId.toString()));
    }

    // ---- Messages ----

    @Test
    void only_participants_can_read_and_write_messages() {
        TestAccount owner = registerAndLogin();
        TestAccount stranger = registerAndLogin();
        UUID adventureId = createAdventure(owner, "PUBLIC");
        Map<String, Object> message = Map.of("adventureId", adventureId.toString(), "content", "hello");

        Assertions.assertEquals(403, call(HttpMethod.POST, "/messages", stranger.token(), message).getStatusCode().value());
        Assertions.assertEquals(403,
                call(HttpMethod.GET, "/messages?adventureId=" + adventureId, stranger.token(), null).getStatusCode().value());

        ResponseEntity<String> sent = call(HttpMethod.POST, "/messages", owner.token(), message);
        Assertions.assertEquals(201, sent.getStatusCode().value(), sent.getBody());

        ResponseEntity<String> read = call(HttpMethod.GET, "/messages?adventureId=" + adventureId, owner.token(), null);
        Assertions.assertEquals(200, read.getStatusCode().value());
        JsonNode first = body(read).get("content").get(0);
        Assertions.assertEquals(owner.id().toString(), first.get("senderId").asString());
    }

    // ---- WebRTC signalling ----

    @Test
    void only_participants_can_send_webrtc_signals() {
        TestAccount owner = registerAndLogin();
        TestAccount stranger = registerAndLogin();
        UUID adventureId = createAdventure(owner, "PUBLIC");
        Map<String, Object> signal = Map.of("type", "offer", "content", "sdp", "adventureId", adventureId.toString(),
                "accountId", owner.id().toString());

        Assertions.assertEquals(403, call(HttpMethod.POST, "/webrtc/offer", stranger.token(), signal).getStatusCode().value());
        Assertions.assertEquals(204, call(HttpMethod.POST, "/webrtc/offer", owner.token(), signal).getStatusCode().value());
    }
}
