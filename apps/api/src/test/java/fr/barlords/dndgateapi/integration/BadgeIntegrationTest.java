package fr.barlords.dndgateapi.integration;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import tools.jackson.databind.JsonNode;

/** The badges shown next to a pseudo: the founder one is stored, the plan one follows the plan in force. */
class BadgeIntegrationTest extends ApiIntegrationTestSupport {

    private JsonNode seenByOthers(TestAccount viewer, TestAccount target) {
        ResponseEntity<String> byId = call(HttpMethod.GET, "/accounts/id/" + target.id(), viewer.token(), null);
        Assertions.assertEquals(200, byId.getStatusCode().value(), byId.getBody());
        ResponseEntity<String> byPseudo = call(HttpMethod.GET, "/accounts/pseudo/" + target.pseudo(), viewer.token(), null);
        Assertions.assertEquals(200, byPseudo.getStatusCode().value(), byPseudo.getBody());
        Assertions.assertEquals(body(byId), body(byPseudo));
        return body(byId);
    }

    private JsonNode mine(TestAccount account) {
        ResponseEntity<String> response = call(HttpMethod.GET, "/accounts/me", account.token(), null);
        Assertions.assertEquals(200, response.getStatusCode().value(), response.getBody());
        return body(response);
    }

    @Test
    void a_founder_set_in_the_database_is_seen_by_others() {
        TestAccount founder = registerAndLogin();
        TestAccount viewer = registerAndLogin();
        Assertions.assertFalse(seenByOthers(viewer, founder).get("founder").asBoolean());

        jdbc.update("UPDATE accounts SET founder = true WHERE id = ?", founder.id());

        Assertions.assertTrue(seenByOthers(viewer, founder).get("founder").asBoolean());
        Assertions.assertTrue(mine(founder).get("founder").asBoolean());
        Assertions.assertFalse(mine(viewer).get("founder").asBoolean());
    }

    @Test
    void the_plan_badge_is_seen_by_others_while_the_plan_is_effective() {
        TestAccount member = registerAndLogin();
        TestAccount viewer = registerAndLogin();
        JsonNode before = seenByOthers(viewer, member);
        Assertions.assertTrue(before.has("planBadge"));
        Assertions.assertTrue(before.get("planBadge").isNull());

        subscribe(member, "test-paid");

        Assertions.assertEquals("legend", seenByOthers(viewer, member).get("planBadge").asString());
        Assertions.assertEquals("legend", mine(member).get("planBadge").asString());

        jdbc.update("UPDATE subscription SET status = 'CANCELED' WHERE account_id = ?", member.id());

        JsonNode after = seenByOthers(viewer, member);
        Assertions.assertTrue(after.has("planBadge"));
        Assertions.assertTrue(after.get("planBadge").isNull());
        Assertions.assertTrue(mine(member).get("planBadge").isNull());
    }

    @Test
    void anonymizing_an_account_removes_its_founder_badge() {
        TestAccount founder = registerAndLogin();
        TestAccount viewer = registerAndLogin();
        jdbc.update("UPDATE accounts SET founder = true WHERE id = ?", founder.id());

        Assertions.assertEquals(204, deleteAccount(founder));

        ResponseEntity<String> byId = call(HttpMethod.GET, "/accounts/id/" + founder.id(), viewer.token(), null);
        Assertions.assertEquals(200, byId.getStatusCode().value(), byId.getBody());
        Assertions.assertFalse(body(byId).get("founder").asBoolean());
        Assertions.assertEquals(Boolean.FALSE,
                jdbc.queryForObject("SELECT founder FROM accounts WHERE id = ?", Boolean.class, founder.id()));
    }
}
