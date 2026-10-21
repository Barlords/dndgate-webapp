package fr.barlords.dndgateapi.integration;

import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

import java.util.UUID;

/**
 * A list must cost the same number of SQL statements whatever the number of rows: the adventure each character
 * belongs to is read with the characters, not once per character (the classic N+1).
 */
class QueryCountIntegrationTest extends ApiIntegrationTestSupport {

    @Autowired
    EntityManagerFactory entityManagerFactory;

    /** A player with one character in each of {@code adventures} different adventures. */
    private TestAccount playerInAdventures(int adventures) {
        TestAccount master = registerAndLogin();
        TestAccount player = registerAndLogin();
        for (int i = 0; i < adventures; i++) {
            UUID adventureId = createAdventure(master, "PUBLIC");
            Assertions.assertEquals(200, call(HttpMethod.POST, "/adventures/" + adventureId + "/join", player.token(), null)
                    .getStatusCode().value());
            Assertions.assertEquals(201, createCharacter(player, adventureId, "Hero " + i).getStatusCode().value());
        }
        return player;
    }

    private long statementsFor(TestAccount account, String path) {
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.setStatisticsEnabled(true);
        // one call first: the first request of an account pays for things that do not depend on the rows
        call(HttpMethod.GET, path, account.token(), null);
        statistics.clear();

        ResponseEntity<String> response = call(HttpMethod.GET, path, account.token(), null);

        Assertions.assertEquals(200, response.getStatusCode().value(), response.getBody());
        return statistics.getPrepareStatementCount();
    }

    @Test
    void my_characters_cost_the_same_number_of_statements_for_two_adventures_or_six() {
        TestAccount few = playerInAdventures(2);
        TestAccount many = playerInAdventures(6);

        Assertions.assertEquals(statementsFor(few, "/game-characters/mine"), statementsFor(many, "/game-characters/mine"));
    }

    @Test
    void the_characters_of_an_owner_cost_the_same_number_of_statements_for_two_adventures_or_six() {
        TestAccount few = playerInAdventures(2);
        TestAccount many = playerInAdventures(6);

        Assertions.assertEquals(
                statementsFor(few, "/game-characters?ownerId=" + few.id()),
                statementsFor(many, "/game-characters?ownerId=" + many.id()));
    }
}
