package fr.barlords.dndgateapi.integration;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.util.UUID;

/**
 * One route lists characters, by owner or by adventure: exactly one of the two filters is given.
 */
class GameCharacterListIntegrationTest extends ApiIntegrationTestSupport {

    @Test
    void a_list_of_characters_needs_exactly_one_filter() {
        TestAccount master = registerAndLogin();
        UUID adventureId = createAdventure(master, "PUBLIC");

        assertBadRequest(call(HttpMethod.GET, "/game-characters", master.token(), null));
        assertBadRequest(call(HttpMethod.GET,
                "/game-characters?ownerId=" + master.id() + "&adventureId=" + adventureId, master.token(), null));

        Assertions.assertEquals(200, call(HttpMethod.GET, "/game-characters?ownerId=" + master.id(), master.token(), null)
                .getStatusCode().value());
        Assertions.assertEquals(200, call(HttpMethod.GET, "/game-characters?adventureId=" + adventureId, master.token(), null)
                .getStatusCode().value());
    }

    private void assertBadRequest(ResponseEntity<String> response) {
        Assertions.assertEquals(400, response.getStatusCode().value(), response.getBody());
        Assertions.assertTrue(MediaType.APPLICATION_PROBLEM_JSON.isCompatibleWith(response.getHeaders().getContentType()));
        Assertions.assertEquals("Give exactly one of ownerId or adventureId", body(response).get("detail").asString());
        Assertions.assertNotNull(body(response).get("correlationId"));
    }
}
