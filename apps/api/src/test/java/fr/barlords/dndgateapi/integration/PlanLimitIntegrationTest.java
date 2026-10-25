package fr.barlords.dndgateapi.integration;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import tools.jackson.databind.JsonNode;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

/** The account quotas come from the plan: an account on the tight test plan (every limit 1) is refused its second item. */
class PlanLimitIntegrationTest extends ApiIntegrationTestSupport {

    private TestAccount tightAccount() {
        TestAccount account = registerAndLogin();
        subscribe(account, "test-tight");
        return account;
    }

    private void assertPlanProblem(ResponseEntity<String> response, String limit) {
        Assertions.assertEquals(409, response.getStatusCode().value(), response.getBody());
        Assertions.assertTrue(response.getHeaders().getContentType().toString().contains("problem+json"));
        JsonNode problem = body(response);
        Assertions.assertEquals("urn:dndgate:plan-limit-reached", problem.get("type").asString());
        Assertions.assertEquals(limit, problem.get("limit").asString());
        Assertions.assertEquals(1, problem.get("max").asInt());
        Assertions.assertEquals("test-tight", problem.get("planId").asString());
        Assertions.assertEquals("test-paid", problem.get("upgradePlanId").asString());
    }

    private ResponseEntity<String> createAdventureRaw(TestAccount owner) {
        return call(HttpMethod.POST, "/adventures", owner.token(), Map.of(
                "name", "Quest", "description", "A quest", "maxPlayer", 4,
                "visibility", "PRIVATE", "postalCode", "75001", "city", "Paris"));
    }

    private ResponseEntity<String> createSpellBook(TestAccount account) {
        return call(HttpMethod.POST, "/spellbooks", account.token(), Map.of("name", "Book", "description", "Spells", "ruleset", "dnd5e-2014"));
    }

    private ResponseEntity<String> importSpellBook(TestAccount account) {
        byte[] file = "{\"name\":\"Imported\",\"description\":\"d\",\"spellList\":[\"fireball\"]}".getBytes(StandardCharsets.UTF_8);
        LinkedMultiValueMap<String, Object> parts = new LinkedMultiValueMap<>();
        parts.add("file", new ByteArrayResource(file) {
            @Override
            public String getFilename() {
                return "book.json";
            }
        });
        return client().post().uri("/spellbooks/import-file")
                .headers(headers -> headers.setBearerAuth(account.token()))
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(parts)
                .retrieve().toEntity(String.class);
    }

    private ResponseEntity<String> createMagicItem(TestAccount account) {
        return call(HttpMethod.POST, "/magic-items", account.token(), Map.of("name", "Ring", "type", "RING", "rarity", "RARE",
                "requiresAttunement", true, "description", "+1 to AC"));
    }

    @Test
    void a_second_active_adventure_is_refused_with_the_plan_problem() {
        TestAccount master = tightAccount();
        Assertions.assertEquals(201, createAdventureRaw(master).getStatusCode().value());

        assertPlanProblem(createAdventureRaw(master), "activeAdventuresAsMaster");

        Assertions.assertEquals(201, createAdventureRaw(registerAndLogin()).getStatusCode().value(), "the limit is per account");
    }

    @Test
    void a_finished_adventure_frees_its_place() {
        TestAccount master = tightAccount();
        UUID first = createAdventure(master, "PRIVATE");
        assertPlanProblem(createAdventureRaw(master), "activeAdventuresAsMaster");

        for (String status : new String[]{"ONGOING", "FINISH"}) {
            Assertions.assertEquals(200, call(HttpMethod.PUT, "/adventures/" + first + "/status", master.token(),
                    Map.of("status", status)).getStatusCode().value());
        }

        Assertions.assertEquals(201, createAdventureRaw(master).getStatusCode().value(), "a finished adventure no longer counts");
        assertPlanProblem(createAdventureRaw(master), "activeAdventuresAsMaster");
    }

    @Test
    void an_adventure_without_game_master_does_not_count_in_the_active_limit() {
        TestAccount master = tightAccount();
        UUID first = createAdventure(master, "PRIVATE");
        assertPlanProblem(createAdventureRaw(master), "activeAdventuresAsMaster");

        jdbc.update("update adventure set master_vacant_since = now() where id = ?", first);

        Assertions.assertEquals(201, createAdventureRaw(master).getStatusCode().value());
    }

    @Test
    void a_second_character_in_the_reserve_is_refused_with_the_plan_problem() {
        TestAccount player = tightAccount();
        Assertions.assertEquals(201, createCharacter(player, null, "One").getStatusCode().value());

        assertPlanProblem(createCharacter(player, null, "Two"), "charactersWithoutAdventure");

        UUID adventureId = createAdventure(player, "PRIVATE");
        Assertions.assertEquals(201, createCharacter(player, adventureId, "In adventure").getStatusCode().value(),
                "the reserve quota does not count characters in an adventure");
    }

    @Test
    void a_second_spell_book_is_refused_with_the_plan_problem() {
        TestAccount wizard = tightAccount();
        Assertions.assertEquals(201, createSpellBook(wizard).getStatusCode().value());

        assertPlanProblem(createSpellBook(wizard), "spellBooks");
    }

    @Test
    void an_import_counts_as_a_spell_book_for_the_plan() {
        TestAccount wizard = tightAccount();
        Assertions.assertEquals(201, importSpellBook(wizard).getStatusCode().value());

        assertPlanProblem(importSpellBook(wizard), "spellBooks");
        assertPlanProblem(createSpellBook(wizard), "spellBooks");
    }

    @Test
    void a_second_magic_item_is_refused_with_the_plan_problem() {
        TestAccount owner = tightAccount();
        Assertions.assertEquals(201, createMagicItem(owner).getStatusCode().value());

        assertPlanProblem(createMagicItem(owner), "magicItems");
    }

    @Test
    void an_account_that_moves_to_a_higher_plan_can_create_more() {
        TestAccount owner = tightAccount();
        Assertions.assertEquals(201, createMagicItem(owner).getStatusCode().value());
        assertPlanProblem(createMagicItem(owner), "magicItems");

        subscribe(owner, "test-paid");

        Assertions.assertEquals(201, createMagicItem(owner).getStatusCode().value());
    }
}
