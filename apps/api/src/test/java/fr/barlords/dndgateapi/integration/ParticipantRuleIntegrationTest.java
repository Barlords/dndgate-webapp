package fr.barlords.dndgateapi.integration;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.util.LinkedMultiValueMap;
import tools.jackson.databind.JsonNode;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Data scoped to an adventure is only available to its participants (owner or players).
 * Fixture: a PUBLIC adventure with an owner (game master), a player who joined, and a stranger.
 */
class ParticipantRuleIntegrationTest extends ApiIntegrationTestSupport {

    private TestAccount owner;

    private TestAccount player;

    private TestAccount stranger;

    private UUID adventureId;

    @BeforeEach
    void fixture() {
        owner = registerAndLogin();
        player = registerAndLogin();
        stranger = registerAndLogin();
        adventureId = createAdventure(owner, "PUBLIC");

        Assertions.assertEquals(200, call(HttpMethod.POST, "/adventures/" + adventureId + "/join", player.token(), null)
                .getStatusCode().value());
    }

    private int status(HttpMethod method, String path, TestAccount account, Object body) {
        return call(method, path, account.token(), body).getStatusCode().value();
    }

    private UUID idOf(ResponseEntity<String> response) {
        Assertions.assertTrue(response.getStatusCode().is2xxSuccessful(), response.getBody());
        return UUID.fromString(body(response).get("id").asString());
    }

    // ---- Historical notes ----

    @Test
    void historical_notes_are_only_readable_by_participants() {
        UUID noteId = idOf(call(HttpMethod.POST, "/historical-notes", owner.token(),
                Map.of("adventureId", adventureId.toString(), "content", "The party met", "date", "2026-01-01")));

        String byAdventure = "/historical-notes?adventureId=" + adventureId;
        Assertions.assertEquals(200, status(HttpMethod.GET, byAdventure, owner, null));
        Assertions.assertEquals(200, status(HttpMethod.GET, byAdventure, player, null));
        Assertions.assertEquals(403, status(HttpMethod.GET, byAdventure, stranger, null));

        Assertions.assertEquals(200, status(HttpMethod.GET, "/historical-notes/" + noteId, player, null));
        Assertions.assertEquals(403, status(HttpMethod.GET, "/historical-notes/" + noteId, stranger, null));
    }

    // ---- Adventure notes (game master notes) ----

    @Test
    void private_notes_are_only_readable_by_the_game_master() {
        UUID publicNote = idOf(call(HttpMethod.POST, "/adventure-notes", owner.token(), Map.of(
                "adventureId", adventureId.toString(), "content", "shared", "date", "2026-01-01", "visibility", "PUBLIC")));
        UUID privateNote = idOf(call(HttpMethod.POST, "/adventure-notes", owner.token(), Map.of(
                "adventureId", adventureId.toString(), "content", "secret", "date", "2026-01-01", "visibility", "PRIVATE")));

        String byAdventure = "/adventure-notes?adventureId=" + adventureId;
        String ownerView = call(HttpMethod.GET, byAdventure, owner.token(), null).getBody();
        String playerView = call(HttpMethod.GET, byAdventure, player.token(), null).getBody();

        Assertions.assertTrue(ownerView.contains("secret") && ownerView.contains("shared"));
        Assertions.assertTrue(playerView.contains("shared"));
        Assertions.assertFalse(playerView.contains("secret"), "a player must not see the private notes");
        Assertions.assertEquals(403, status(HttpMethod.GET, byAdventure, stranger, null));

        Assertions.assertEquals(200, status(HttpMethod.GET, "/adventure-notes/" + publicNote, player, null));
        Assertions.assertEquals(403, status(HttpMethod.GET, "/adventure-notes/" + privateNote, player, null));
        Assertions.assertEquals(200, status(HttpMethod.GET, "/adventure-notes/" + privateNote, owner, null));
        Assertions.assertEquals(403, status(HttpMethod.GET, "/adventure-notes/" + publicNote, stranger, null));
    }

    // ---- Characters ----

    private ResponseEntity<String> createCharacter(TestAccount account) {
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
        String request = json.writeValueAsString(Map.of(
                "adventureId", adventureId.toString(), "name", "Gandalf", "race", "Human", "martialClass", "Wizard",
                "personality", "Wise", "background", "Old"));

        LinkedMultiValueMap<String, Object> parts = new LinkedMultiValueMap<>();
        parts.add("picturePresentation", new HttpEntity<>(picture, pictureHeaders));
        parts.add("createGameCharacterRequest", new HttpEntity<>(request, requestHeaders));

        return client().post().uri("/game-characters")
                .headers(headers -> headers.setBearerAuth(account.token()))
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(parts)
                .retrieve().toEntity(String.class);
    }

    @Test
    void only_participants_can_create_a_character_in_an_adventure() {
        Assertions.assertEquals(403, createCharacter(stranger).getStatusCode().value());
        Assertions.assertTrue(createCharacter(player).getStatusCode().is2xxSuccessful());
    }

    @Test
    void characters_are_only_readable_by_participants_and_their_owner() {
        UUID characterId = idOf(createCharacter(player));

        String byAdventure = "/game-characters?adventureId=" + adventureId;
        Assertions.assertEquals(200, status(HttpMethod.GET, byAdventure, owner, null));
        Assertions.assertEquals(200, status(HttpMethod.GET, byAdventure, player, null));
        Assertions.assertEquals(403, status(HttpMethod.GET, byAdventure, stranger, null));

        Assertions.assertEquals(200, status(HttpMethod.GET, "/game-characters/" + characterId, owner, null));
        Assertions.assertEquals(200, status(HttpMethod.GET, "/game-characters/" + characterId, player, null));
        Assertions.assertEquals(403, status(HttpMethod.GET, "/game-characters/" + characterId, stranger, null));
    }

    @Test
    void a_caller_can_only_list_its_own_characters() {
        createCharacter(player);

        Assertions.assertEquals(200, status(HttpMethod.GET, "/game-characters?ownerId=" + player.id(), player, null));
        Assertions.assertEquals(403, status(HttpMethod.GET, "/game-characters?ownerId=" + player.id(), stranger, null));
        Assertions.assertEquals(403, status(HttpMethod.GET, "/game-characters?ownerId=" + player.id(), owner, null));
    }

    // ---- Spell books ----

    @Test
    void spell_books_are_personal() {
        UUID bookId = idOf(call(HttpMethod.POST, "/spellbooks", player.token(),
                Map.of("name", "My book", "description", "Fireballs", "ruleset", "dnd5e-2014")));

        Assertions.assertEquals(200, status(HttpMethod.GET, "/spellbooks/" + bookId, player, null));
        Assertions.assertEquals(200, status(HttpMethod.GET, "/spellbooks/" + bookId + "/export-file", player, null));
        Assertions.assertEquals(200, status(HttpMethod.GET, "/spellbooks?ownerId=" + player.id(), player, null));

        Assertions.assertEquals(403, status(HttpMethod.GET, "/spellbooks/" + bookId, owner, null));
        Assertions.assertEquals(403, status(HttpMethod.GET, "/spellbooks/" + bookId + "/export-file", stranger, null));
        Assertions.assertEquals(403, status(HttpMethod.GET, "/spellbooks?ownerId=" + player.id(), stranger, null));
    }

    @Test
    void the_list_of_every_spell_book_is_not_reachable() {
        Assertions.assertTrue(call(HttpMethod.GET, "/spellbooks", player.token(), null).getStatusCode().is4xxClientError());
    }

    // ---- Planning ----

    private UUID createPlanning(TestAccount account, UUID forAdventure) {
        return idOf(call(HttpMethod.POST, "/planning", account.token(),
                Map.of("adventureId", forAdventure.toString(), "propositionDates", List.of("2026-02-01", "2026-02-08"))));
    }

    @Test
    void only_the_game_master_can_create_or_change_a_planning() {
        Assertions.assertEquals(403, status(HttpMethod.POST, "/planning", stranger,
                Map.of("adventureId", adventureId.toString(), "propositionDates", List.of("2026-02-01"))));
        Assertions.assertEquals(403, status(HttpMethod.PUT, "/planning", player,
                Map.of("adventureId", adventureId.toString(), "propositionDates", List.of("2026-02-01"))));

        createPlanning(owner, adventureId);
        Assertions.assertTrue(call(HttpMethod.PUT, "/planning", owner.token(),
                Map.of("adventureId", adventureId.toString(), "propositionDates", List.of("2026-03-01"))).getStatusCode().is2xxSuccessful());
    }

    @Test
    void the_planning_of_a_public_adventure_is_visible_to_candidates_but_a_private_one_is_not() {
        UUID planningId = createPlanning(owner, adventureId);
        UUID privateAdventure = createAdventure(owner, "PRIVATE");
        UUID privatePlanning = createPlanning(owner, privateAdventure);

        Assertions.assertEquals(200, status(HttpMethod.GET, "/planning?adventureId=" + adventureId, stranger, null));
        Assertions.assertEquals(200, status(HttpMethod.GET, "/planning/" + planningId, stranger, null));

        Assertions.assertEquals(404, status(HttpMethod.GET, "/planning?adventureId=" + privateAdventure, stranger, null));
        Assertions.assertEquals(404, status(HttpMethod.GET, "/planning/" + privatePlanning, stranger, null));
        Assertions.assertEquals(200, status(HttpMethod.GET, "/planning/" + privatePlanning, owner, null));
    }

    @Test
    void every_planning_listing_is_not_reachable() {
        Assertions.assertTrue(call(HttpMethod.GET, "/planning", owner.token(), null).getStatusCode().is4xxClientError());
    }

    @Test
    void candidates_can_vote_on_a_visible_planning_but_not_on_a_hidden_one() {
        UUID planningId = createPlanning(owner, adventureId);
        UUID privatePlanning = createPlanning(owner, createAdventure(owner, "PRIVATE"));

        Assertions.assertTrue(call(HttpMethod.PUT, "/planning/availability", stranger.token(),
                Map.of("planningId", planningId.toString(), "propositionDates", List.of("2026-02-01"))).getStatusCode().is2xxSuccessful());
        Assertions.assertEquals(404, status(HttpMethod.PUT, "/planning/availability", stranger,
                Map.of("planningId", privatePlanning.toString(), "propositionDates", List.of("2026-02-01"))));
        Assertions.assertEquals(404, status(HttpMethod.POST, "/planning/availability", stranger,
                Map.of("planningId", privatePlanning.toString(), "propositionDates", List.of("2026-02-01"))));
    }

    @Test
    void votes_are_only_readable_by_participants() {
        UUID planningId = createPlanning(owner, adventureId);
        call(HttpMethod.PUT, "/planning/availability", player.token(),
                Map.of("planningId", planningId.toString(), "propositionDates", List.of("2026-02-01")));

        String votes = "/planning/" + planningId + "/availability";
        ResponseEntity<String> asOwner = call(HttpMethod.GET, votes, owner.token(), null);
        Assertions.assertEquals(200, asOwner.getStatusCode().value());
        JsonNode list = body(asOwner);
        Assertions.assertEquals(1, list.size());

        Assertions.assertEquals(200, status(HttpMethod.GET, votes, player, null));
        Assertions.assertEquals(403, status(HttpMethod.GET, votes, stranger, null));
    }
}
