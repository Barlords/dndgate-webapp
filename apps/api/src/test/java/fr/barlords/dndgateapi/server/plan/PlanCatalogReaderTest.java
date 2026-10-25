package fr.barlords.dndgateapi.server.plan;

import fr.barlords.dndgateapi.domain.functional.model.plan.BillingInterval;
import fr.barlords.dndgateapi.domain.functional.model.plan.Feature;
import fr.barlords.dndgateapi.domain.functional.model.plan.Plan;
import fr.barlords.dndgateapi.domain.functional.model.plan.PlanBadge;
import fr.barlords.dndgateapi.domain.functional.model.plan.PlanCatalog;
import fr.barlords.dndgateapi.domain.functional.model.plan.PlanLimit;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlanCatalogReaderTest {

    private static final String LIMITS = """
            "limits": {"activeAdventuresAsMaster": 1, "charactersWithoutAdventure": 1, "spellBooks": 1,
              "magicItems": 1, "masterNotesPerAdventure": 1, "historicalNotesPerAdventure": 1,
              "charactersPerPlayerAndAdventure": 1, "npcsPerAdventure": 1, "messagesPerAdventure": 1}""";
    private static final String FEATURES = """
            "features": {"characterPictures": false, "video": false, "equippedTable": false,
              "earlyAccess": false, "prioritySupport": false}""";

    private final PlanCatalogReader reader = new PlanCatalogReader();

    private PlanCatalog read(String plan) {
        String json = "{\"currency\": \"EUR\", \"defaultPlan\": \"free\", \"plans\": [" + plan + "]}";
        return reader.read(new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));
    }

    private static String plan(String limits, String features, String badge, String prices) {
        return "{\"id\": \"free\", " + prices + limits + ", " + features + ", \"badge\": " + badge + "}";
    }

    @Test
    void the_real_catalog_holds_the_offers_of_the_design() throws IOException {
        PlanCatalog catalog;
        try (InputStream input = getClass().getResourceAsStream("/plans/plans.json")) {
            catalog = reader.read(input);
        }

        assertEquals("EUR", catalog.currency());
        assertEquals("adventurer", catalog.defaultPlanId());
        assertEquals(List.of("adventurer", "hero", "dungeon-master", "legendary"),
                catalog.plans().stream().map(Plan::id).toList());

        int[] monthly = {0, 99, 499, 999};
        int[] yearly = {0, 990, 4990, 9990};
        String[] keys = {"adventurer", "hero", "dungeon_master", "legendary"};
        for (int i = 0; i < 4; i++) {
            Plan plan = catalog.plans().get(i);
            if (i == 0) {
                assertTrue(plan.isFree());
                continue;
            }
            assertEquals(monthly[i], plan.prices().get(BillingInterval.MONTHLY).amount());
            assertEquals(keys[i] + "_monthly", plan.prices().get(BillingInterval.MONTHLY).stripeLookupKey());
            assertEquals(yearly[i], plan.prices().get(BillingInterval.YEARLY).amount());
            assertEquals(keys[i] + "_yearly", plan.prices().get(BillingInterval.YEARLY).stripeLookupKey());
        }

        assertLimits(catalog, PlanLimit.ACTIVE_ADVENTURES_AS_MASTER, 1, 2, 10, 25);
        assertLimits(catalog, PlanLimit.CHARACTERS_WITHOUT_ADVENTURE, 3, 15, 50, 75);
        assertLimits(catalog, PlanLimit.SPELL_BOOKS, 2, 10, 50, 100);
        assertLimits(catalog, PlanLimit.MAGIC_ITEMS, 10, 50, 200, 500);
        assertLimits(catalog, PlanLimit.MASTER_NOTES_PER_ADVENTURE, 50, 100, 500, 500);
        assertLimits(catalog, PlanLimit.HISTORICAL_NOTES_PER_ADVENTURE, 50, 100, 500, 500);
        assertLimits(catalog, PlanLimit.CHARACTERS_PER_PLAYER_AND_ADVENTURE, 1, 1, 5, 10);
        assertLimits(catalog, PlanLimit.NPCS_PER_ADVENTURE, 5, 10, 50, 100);
        assertLimits(catalog, PlanLimit.MESSAGES_PER_ADVENTURE, 200, 200, 1000, 2000);

        assertFeatures(catalog, Feature.CHARACTER_PICTURES, false, true, true, true);
        assertFeatures(catalog, Feature.VIDEO, false, false, true, true);
        assertFeatures(catalog, Feature.EQUIPPED_TABLE, false, false, true, true);
        assertFeatures(catalog, Feature.EARLY_ACCESS, false, false, false, true);
        assertFeatures(catalog, Feature.PRIORITY_SUPPORT, false, false, false, true);

        assertNull(catalog.plans().get(0).badge());
        assertNull(catalog.plans().get(1).badge());
        assertEquals(PlanBadge.DUNGEON_MASTER, catalog.plans().get(2).badge());
        assertEquals(PlanBadge.LEGEND, catalog.plans().get(3).badge());
        assertEquals(List.of(false, false, true, false), catalog.plans().stream().map(Plan::highlighted).toList());
    }

    private static void assertLimits(PlanCatalog catalog, PlanLimit limit, int... expected) {
        for (int i = 0; i < expected.length; i++) {
            assertEquals(expected[i], catalog.plans().get(i).limit(limit), limit + " of plan " + i);
        }
    }

    private static void assertFeatures(PlanCatalog catalog, Feature feature, boolean... expected) {
        for (int i = 0; i < expected.length; i++) {
            assertEquals(expected[i], catalog.plans().get(i).has(feature), feature + " of plan " + i);
        }
    }

    @Test
    void the_test_catalog_is_readable() throws IOException {
        try (InputStream input = getClass().getResourceAsStream("/plans/test-plans.json")) {
            PlanCatalog catalog = reader.read(input);
            assertEquals("test-default", catalog.defaultPlan().id());
            assertEquals(4, catalog.plans().size());
            assertFalse(catalog.plans().get(2).has(Feature.VIDEO));
        }
    }

    @Test
    void a_minimal_plan_is_read() {
        PlanCatalog catalog = read(plan(LIMITS, FEATURES, "null", ""));

        assertEquals("free", catalog.defaultPlan().id());
        assertFalse(catalog.defaultPlan().highlighted());
    }

    @Test
    void an_unknown_limit_key_is_refused() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> read(plan(LIMITS.replace("spellBooks", "spellTomes"), FEATURES, "null", "")));
        assertTrue(error.getMessage().contains("spellTomes"));
    }

    @Test
    void an_unknown_feature_key_is_refused() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> read(plan(LIMITS, FEATURES.replace("video", "hologram"), "null", "")));
        assertTrue(error.getMessage().contains("hologram"));
    }

    @Test
    void an_unknown_badge_is_refused() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> read(plan(LIMITS, FEATURES, "\"wizard\"", "")));
        assertTrue(error.getMessage().contains("wizard"));
    }

    @Test
    void an_unknown_interval_is_refused() {
        String prices = "\"prices\": {\"weekly\": {\"amount\": 1, \"stripeLookupKey\": \"k\"}}, ";
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> read(plan(LIMITS, FEATURES, "null", prices)));
        assertTrue(error.getMessage().contains("weekly"));
    }

    private PlanCatalog readRaw(String json) {
        return reader.read(new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));
    }

    private void assertRefusedNaming(String key, String json) {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> readRaw(json), json);
        assertTrue(error.getMessage().contains(key), error.getMessage());
    }

    private static String catalog(String plan) {
        return "{\"currency\": \"EUR\", \"defaultPlan\": \"free\", \"plans\": [" + plan + "]}";
    }

    private static final String PRICES = "\"prices\": {\"monthly\": {\"amount\": 100, \"stripeLookupKey\": \"paid_monthly\"}}, ";

    /** The free default plan and a paid plan with the prices. */
    private static String pricedCatalog(String prices) {
        return catalog(plan(LIMITS, FEATURES, "null", "") + ", "
                + plan(LIMITS, FEATURES, "null", prices).replace("\"id\": \"free\"", "\"id\": \"paid\""));
    }

    @Test
    void a_limit_must_be_a_whole_number() {
        for (String value : new String[]{"\"1\"", "1.5", "2.0", "true", "null", "3000000000"}) {
            assertRefusedNaming("spellBooks", catalog(plan(LIMITS.replace("\"spellBooks\": 1", "\"spellBooks\": " + value),
                    FEATURES, "null", "")));
        }
    }

    @Test
    void an_amount_must_be_a_whole_number_and_a_lookup_key_a_string() {
        assertEquals(100, readRaw(pricedCatalog(PRICES)).find("paid").orElseThrow()
                .prices().get(BillingInterval.MONTHLY).amount());
        assertRefusedNaming("amount", pricedCatalog(PRICES.replace("100", "99.9")));
        assertRefusedNaming("amount", pricedCatalog(PRICES.replace("100", "\"100\"")));
        assertRefusedNaming("stripeLookupKey", pricedCatalog(PRICES.replace("\"paid_monthly\"", "42")));
    }

    @Test
    void a_feature_and_highlighted_must_be_booleans() {
        for (String value : new String[]{"\"true\"", "1", "null"}) {
            assertRefusedNaming("video", catalog(plan(LIMITS, FEATURES.replace("\"video\": false", "\"video\": " + value),
                    "null", "")));
        }
        assertRefusedNaming("highlighted", catalog(plan(LIMITS, FEATURES, "null", "\"highlighted\": \"yes\", ")));
        assertRefusedNaming("highlighted", catalog(plan(LIMITS, FEATURES, "null", "\"highlighted\": 1, ")));
    }

    @Test
    void the_texts_must_be_strings() {
        assertRefusedNaming("currency", catalog(plan(LIMITS, FEATURES, "null", "")).replace("\"EUR\"", "978"));
        assertRefusedNaming("defaultPlan", catalog(plan(LIMITS, FEATURES, "null", "")).replace("\"defaultPlan\": \"free\"",
                "\"defaultPlan\": 1"));
        assertRefusedNaming("id", catalog(plan(LIMITS, FEATURES, "null", "")).replace("\"id\": \"free\"", "\"id\": 1"));
        assertRefusedNaming("badge", catalog(plan(LIMITS, FEATURES, "true", "")));
    }

    @Test
    void a_missing_required_key_is_named() {
        String valid = pricedCatalog(PRICES);
        assertEquals(2, readRaw(valid).plans().size());
        assertRefusedNaming("currency", valid.replace("\"currency\": \"EUR\", ", ""));
        assertRefusedNaming("defaultPlan", valid.replace("\"defaultPlan\": \"free\", ", ""));
        assertRefusedNaming("plans", "{\"currency\": \"EUR\", \"defaultPlan\": \"free\"}");
        assertRefusedNaming("plans", "{\"currency\": \"EUR\", \"defaultPlan\": \"free\", \"plans\": {}}");
        assertRefusedNaming("id", valid.replace("\"id\": \"free\", ", ""));
        assertRefusedNaming("amount", valid.replace("\"amount\": 100, ", ""));
        assertRefusedNaming("stripeLookupKey", valid.replace(", \"stripeLookupKey\": \"paid_monthly\"", ""));
    }
}
