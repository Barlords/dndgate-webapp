package fr.barlords.dndgateapi.integration;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;

/**
 * What the API tells a person about their own account: nothing more than what the pages use.
 */
class ProfileShapeIntegrationTest extends ApiIntegrationTestSupport {

    @Test
    void the_profile_holds_only_the_identity_and_the_badges_of_the_account() {
        TestAccount account = registerAndLogin();

        ResponseEntity<String> response = call(HttpMethod.GET, "/accounts/me", account.token(), null);

        Assertions.assertEquals(200, response.getStatusCode().value());
        JsonNode profile = body(response);
        List<String> fields = new ArrayList<>(profile.propertyNames());
        fields.sort(String::compareTo);
        Assertions.assertEquals(List.of("email", "founder", "id", "pendingEmail", "planBadge", "pseudo"), fields);
        Assertions.assertEquals(account.pseudo(), profile.get("pseudo").asString());
    }
}
