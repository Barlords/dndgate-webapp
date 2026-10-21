package fr.barlords.dndgateapi.integration;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

import java.util.List;
import java.util.Map;

class NamingIntegrationTest extends ApiIntegrationTestSupport {

    @Test
    void the_availability_tables_follow_the_name_of_the_code() {
        for (String table : List.of("availability", "availability_dates")) {
            Assertions.assertEquals(1, jdbc.queryForObject("select count(*) from information_schema.tables where table_name = ?", Integer.class, table), table);
        }
        for (String old : List.of("disponibility", "disponibility_dates")) {
            Assertions.assertEquals(0, jdbc.queryForObject("select count(*) from information_schema.tables where table_name = ?", Integer.class, old), old);
        }
    }

    @Test
    void the_availability_route_exists_and_the_old_one_does_not() {
        TestAccount account = registerAndLogin();

        // Validated before anything else: a body with no date is a 400, not a 404
        Assertions.assertEquals(400, call(HttpMethod.PUT, "/planning/availability", account.token(), Map.of("planningId", java.util.UUID.randomUUID().toString(), "propositionDates", List.of())).getStatusCode().value());
        int old = call(HttpMethod.PUT, "/planning/disponibility", account.token(), Map.of("planningId", java.util.UUID.randomUUID().toString(), "propositionDates", List.of("2027-01-01"))).getStatusCode().value();
        Assertions.assertTrue(old == 404 || old == 405, "the old route must be gone, got " + old);
    }
}
