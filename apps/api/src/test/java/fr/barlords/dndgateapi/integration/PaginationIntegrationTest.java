package fr.barlords.dndgateapi.integration;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import tools.jackson.databind.JsonNode;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

class PaginationIntegrationTest extends ApiIntegrationTestSupport {

    private TestAccount master;

    private TestAccount player;

    private TestAccount stranger;

    @BeforeEach
    void fixture() {
        master = registerAndLogin();
        player = registerAndLogin();
        stranger = registerAndLogin();
    }

    private UUID createAdventure(TestAccount owner, String name, String visibility) {
        ResponseEntity<String> response = call(HttpMethod.POST, "/adventures", owner.token(), Map.of(
                "name", name, "description", "A quest", "maxPlayer", 4,
                "visibility", visibility, "postalCode", "75001", "city", "Paris"));
        Assertions.assertEquals(201, response.getStatusCode().value(), response.getBody());
        return UUID.fromString(body(response).get("id").asString());
    }

    private JsonNode page(String path, TestAccount account) {
        ResponseEntity<String> response = call(HttpMethod.GET, path, account.token(), null);
        Assertions.assertEquals(200, response.getStatusCode().value(), response.getBody());
        return body(response);
    }

    private List<String> field(JsonNode page, String name) {
        List<String> values = new ArrayList<>();
        page.get("content").forEach(node -> values.add(node.get(name).asString()));
        return values;
    }

    private void join(TestAccount account, UUID adventureId) {
        Assertions.assertEquals(200, call(HttpMethod.POST, "/adventures/" + adventureId + "/join", account.token(), null).getStatusCode().value());
    }

    // ---- the shape of a page ----

    @Test
    void a_list_is_cut_in_pages_that_do_not_overlap_and_follow_a_stable_order() {
        for (String name : List.of("E quest", "B quest", "A quest", "D quest", "C quest")) {
            createAdventure(master, name, "PRIVATE");
        }

        JsonNode first = page("/adventures/as-master?page=0&size=2", master);
        JsonNode second = page("/adventures/as-master?page=1&size=2", master);
        JsonNode third = page("/adventures/as-master?page=2&size=2", master);

        Assertions.assertEquals(List.of("A quest", "B quest"), field(first, "name"));
        Assertions.assertEquals(List.of("C quest", "D quest"), field(second, "name"));
        Assertions.assertEquals(List.of("E quest"), field(third, "name"));
        Assertions.assertEquals(5, first.get("totalElements").asInt());
        Assertions.assertEquals(3, first.get("totalPages").asInt());
        Assertions.assertEquals(0, first.get("page").asInt());
        Assertions.assertEquals(2, first.get("size").asInt());
        Assertions.assertEquals(0, page("/adventures/as-master?page=3&size=2", master).get("content").size(), "past the end");
    }

    @Test
    void without_parameters_the_first_twenty_are_returned() {
        for (int i = 0; i < 23; i++) {
            createAdventure(master, String.format("Quest %02d", i), "PRIVATE");
        }

        JsonNode list = page("/adventures/as-master", master);

        Assertions.assertEquals(20, list.get("content").size());
        Assertions.assertEquals(23, list.get("totalElements").asInt());
        Assertions.assertEquals(20, list.get("size").asInt());
    }

    @Test
    void a_page_size_beyond_the_limit_or_a_negative_page_is_refused() {
        for (String query : List.of("size=0", "size=101", "size=-3", "page=-1", "page=abc", "size=abc")) {
            Assertions.assertEquals(400, call(HttpMethod.GET, "/adventures?" + query, master.token(), null).getStatusCode().value(), query);
        }
        Assertions.assertEquals(200, call(HttpMethod.GET, "/adventures?size=100", master.token(), null).getStatusCode().value());
    }

    // ---- the visibility is decided before the cut ----

    @Test
    void the_total_of_the_visible_adventures_counts_only_what_the_account_may_see() {
        createAdventure(master, "Hidden", "PRIVATE");
        createAdventure(master, "Shown", "PUBLIC");

        long publicOnes = jdbc.queryForObject("select count(*) from adventure where visibility = 'PUBLIC'", Long.class);
        JsonNode strangerList = page("/adventures?size=100", stranger);
        JsonNode masterList = page("/adventures?size=100", master);

        Assertions.assertEquals(publicOnes, strangerList.get("totalElements").asLong());
        Assertions.assertFalse(field(strangerList, "name").contains("Hidden"));
        Assertions.assertEquals(
                jdbc.queryForObject("select count(*) from adventure where visibility = 'PUBLIC' or creator_id = ?", Long.class, master.id()),
                masterList.get("totalElements").asLong());
    }

    @Test
    void a_player_sees_the_private_adventure_he_joined_by_code_in_every_list() {
        UUID privateId = createAdventure(master, "Secret", "PRIVATE");
        String code = jdbc.queryForObject("select invitation_code from adventure where id = ?", String.class, privateId);
        Assertions.assertEquals(200, call(HttpMethod.POST, "/adventures/invitations/accept", player.token(), Map.of("code", code))
                .getStatusCode().value());

        Assertions.assertEquals(List.of("Secret"), field(page("/adventures/as-player", player), "name"));
        Assertions.assertTrue(field(page("/adventures?size=100", player), "name").contains("Secret"));
        Assertions.assertEquals(0, page("/adventures/as-player", stranger).get("totalElements").asInt());
    }

    @Test
    void the_available_adventures_leave_out_my_own_the_joined_ones_and_the_ones_that_cannot_be_joined() {
        UUID open = createAdventure(master, "Open", "PUBLIC");
        UUID private_ = createAdventure(master, "Private", "PRIVATE");
        UUID mine = createAdventure(player, "Mine", "PUBLIC");
        UUID started = createAdventure(master, "Started", "PUBLIC");
        jdbc.update("update adventure set status = 'ONGOING' where id = ?", started);
        long before = jdbc.queryForObject("select count(*) from adventure where visibility = 'PUBLIC' and status = 'NOT_STARTED' and creator_id <> ?",
                Long.class, player.id());

        JsonNode available = page("/adventures/available?size=100", player);

        Assertions.assertEquals(before, available.get("totalElements").asLong());
        Assertions.assertTrue(field(available, "name").contains("Open"));
        Assertions.assertFalse(field(available, "name").contains("Mine"));
        Assertions.assertFalse(field(available, "name").contains("Private"));
        Assertions.assertFalse(field(available, "name").contains("Started"));

        join(player, open);

        JsonNode after = page("/adventures/available?size=100", player);
        Assertions.assertEquals(before - 1, after.get("totalElements").asLong());
        Assertions.assertFalse(field(after, "name").contains("Open"));
    }

    @Test
    void the_players_of_every_adventure_of_a_page_are_present() {
        UUID first = createAdventure(master, "A first", "PUBLIC");
        UUID second = createAdventure(master, "B second", "PUBLIC");
        join(player, first);
        join(stranger, first);
        join(player, second);

        JsonNode list = page("/adventures/as-master", master);

        Assertions.assertEquals(2, list.get("content").get(0).get("players").size());
        Assertions.assertEquals(1, list.get("content").get(1).get("players").size());
    }

    // ---- what lives inside an adventure ----

    @Test
    void messages_come_newest_first_and_only_to_participants() {
        UUID adventureId = createAdventure(master, "Chat", "PUBLIC");
        LocalDateTime start = LocalDateTime.of(2026, 1, 1, 10, 0);
        for (int i = 0; i < 5; i++) {
            jdbc.update("insert into message (id, adventure_id, sender_id, content, send_date) values (?, ?, ?, ?, ?)",
                    UUID.randomUUID(), adventureId, master.id(), "message " + i, Timestamp.valueOf(start.plusMinutes(i)));
        }

        JsonNode first = page("/messages?adventureId=" + adventureId + "&size=2", master);
        JsonNode last = page("/messages?adventureId=" + adventureId + "&size=2&page=2", master);

        Assertions.assertEquals(List.of("message 4", "message 3"), field(first, "content"));
        Assertions.assertEquals(List.of("message 0"), field(last, "content"));
        Assertions.assertEquals(5, first.get("totalElements").asInt());
        Assertions.assertEquals(403, call(HttpMethod.GET, "/messages?adventureId=" + adventureId, stranger.token(), null)
                .getStatusCode().value());
    }

    @Test
    void private_notes_are_neither_returned_nor_counted_for_the_players() {
        UUID adventureId = createAdventure(master, "Notes", "PUBLIC");
        join(player, adventureId);
        for (int i = 0; i < 3; i++) {
            insertNote(adventureId, "public " + i, "PUBLIC", LocalDate.of(2026, 1, 1 + i));
        }
        for (int i = 0; i < 2; i++) {
            insertNote(adventureId, "private " + i, "PRIVATE", LocalDate.of(2026, 2, 1 + i));
        }

        JsonNode asPlayer = page("/adventure-notes?adventureId=" + adventureId + "&size=2", player);
        JsonNode asMaster = page("/adventure-notes?adventureId=" + adventureId + "&size=2", master);

        Assertions.assertEquals(3, asPlayer.get("totalElements").asInt());
        Assertions.assertEquals(List.of("public 2", "public 1"), field(asPlayer, "content"));
        Assertions.assertEquals(5, asMaster.get("totalElements").asInt());
        Assertions.assertEquals(List.of("private 1", "private 0"), field(asMaster, "content"));
    }

    private void insertNote(UUID adventureId, String content, String visibility, LocalDate date) {
        jdbc.update("insert into adventure_note (id, adventure_id, content, date, visibility) values (?, ?, ?, ?, ?)",
                UUID.randomUUID(), adventureId, content, date, visibility);
    }

    @Test
    void historical_notes_are_paginated_newest_first() {
        UUID adventureId = createAdventure(master, "History", "PUBLIC");
        for (int i = 0; i < 3; i++) {
            jdbc.update("insert into historical_note (id, adventure_id, content, date) values (?, ?, ?, ?)",
                    UUID.randomUUID(), adventureId, "chapter " + i, LocalDate.of(2026, 3, 1 + i));
        }

        JsonNode first = page("/historical-notes?adventureId=" + adventureId + "&size=2", master);

        Assertions.assertEquals(List.of("chapter 2", "chapter 1"), field(first, "content"));
        Assertions.assertEquals(3, first.get("totalElements").asInt());
        Assertions.assertEquals(403, call(HttpMethod.GET, "/historical-notes?adventureId=" + adventureId, stranger.token(), null)
                .getStatusCode().value());
    }

    @Test
    void ratings_received_are_paginated_and_still_private_to_their_target() {
        for (int i = 0; i < 3; i++) {
            jdbc.update("insert into rating (id, emitter_id, target_id, rate) values (?, ?, ?, ?)",
                    UUID.randomUUID(), UUID.randomUUID(), master.id(), 3 + i % 2);
        }

        JsonNode list = page("/ratings?targetedAccountId=" + master.id() + "&size=2", master);

        Assertions.assertEquals(2, list.get("content").size());
        Assertions.assertEquals(3, list.get("totalElements").asInt());
        Assertions.assertEquals(403, call(HttpMethod.GET, "/ratings?targetedAccountId=" + master.id(), stranger.token(), null)
                .getStatusCode().value());
    }
}
