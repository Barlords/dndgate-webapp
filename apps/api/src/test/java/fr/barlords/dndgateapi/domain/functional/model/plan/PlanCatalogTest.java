package fr.barlords.dndgateapi.domain.functional.model.plan;

import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlanCatalogTest {

    private static final Map<BillingInterval, PlanPrice> PRICE =
            Map.of(BillingInterval.MONTHLY, new PlanPrice(100, "paid_monthly"));

    private static Plan plan(String id, Map<BillingInterval, PlanPrice> prices, int limit, boolean feature) {
        return plan(id, prices, limit, feature, PlanLimit.SPELL_BOOKS);
    }

    private static Plan plan(String id, Map<BillingInterval, PlanPrice> prices, int limit, boolean feature,
                             PlanLimit varying) {
        Map<PlanLimit, Integer> limits = new EnumMap<>(PlanLimit.class);
        for (PlanLimit planLimit : PlanLimit.values()) {
            limits.put(planLimit, planLimit == varying ? limit : 5);
        }
        Map<Feature, Boolean> features = new EnumMap<>(Feature.class);
        for (Feature value : Feature.values()) {
            features.put(value, value == Feature.VIDEO && feature);
        }
        return new Plan(id, false, prices, limits, features, null);
    }

    private static PlanCatalog catalog(Plan... plans) {
        return new PlanCatalog("EUR", "free", List.of(plans));
    }

    private static Plan free() {
        return plan("free", Map.of(), 2, false);
    }

    @Test
    void a_valid_catalog_gives_its_default_plan() {
        PlanCatalog catalog = catalog(free(), plan("paid", PRICE, 10, true));

        assertEquals("free", catalog.defaultPlan().id());
        assertEquals(1, catalog.rank(catalog.find("paid").orElseThrow()));
        assertEquals("free", catalog.findOrDefault("unknown").id());
        assertTrue(catalog.find("unknown").isEmpty());
    }

    @Test
    void a_plan_exposes_its_limits_features_and_badge() {
        Plan plan = plan("paid", PRICE, 10, true);

        assertEquals(10, plan.limit(PlanLimit.SPELL_BOOKS));
        assertTrue(plan.has(Feature.VIDEO));
        assertFalse(plan.isFree());
        assertTrue(plan.badgeIfAny().isEmpty());
        assertTrue(free().isFree());
    }

    @Test
    void a_catalog_is_refused_when_a_plan_id_is_duplicated() {
        assertThrows(IllegalArgumentException.class, () -> catalog(free(), free()));
    }

    @Test
    void a_catalog_is_refused_when_the_default_plan_is_unknown() {
        assertThrows(IllegalArgumentException.class,
                () -> new PlanCatalog("EUR", "nobody", List.of(free(), plan("paid", PRICE, 10, true))));
    }

    @Test
    void a_catalog_is_refused_when_the_default_plan_has_a_price() {
        assertThrows(IllegalArgumentException.class,
                () -> new PlanCatalog("EUR", "paid", List.of(plan("paid", PRICE, 10, true))));
    }

    @Test
    void a_catalog_is_refused_when_a_paid_plan_has_no_price() {
        assertThrows(IllegalArgumentException.class, () -> catalog(free(), plan("paid", Map.of(), 10, true)));
    }

    @Test
    void a_catalog_is_refused_when_empty() {
        assertThrows(IllegalArgumentException.class, () -> catalog());
    }

    @Test
    void a_plan_is_refused_when_a_limit_is_below_one() {
        assertThrows(IllegalArgumentException.class, () -> plan("free", Map.of(), 0, false));
    }

    @Test
    void a_plan_is_refused_when_a_limit_is_missing() {
        Map<PlanLimit, Integer> limits = new EnumMap<>(PlanLimit.class);
        limits.put(PlanLimit.SPELL_BOOKS, 1);
        Map<Feature, Boolean> features = new EnumMap<>(Feature.class);
        for (Feature value : Feature.values()) {
            features.put(value, false);
        }
        assertThrows(IllegalArgumentException.class, () -> new Plan("free", false, Map.of(), limits, features, null));
    }

    @Test
    void a_plan_is_refused_when_a_feature_is_missing() {
        Map<PlanLimit, Integer> limits = new EnumMap<>(PlanLimit.class);
        for (PlanLimit value : PlanLimit.values()) {
            limits.put(value, 1);
        }
        Map<Feature, Boolean> features = new EnumMap<>(Feature.class);
        features.put(Feature.VIDEO, true);
        assertThrows(IllegalArgumentException.class, () -> new Plan("free", false, Map.of(), limits, features, null));
    }

    @Test
    void a_price_is_refused_when_not_positive_or_without_lookup_key() {
        assertThrows(IllegalArgumentException.class, () -> new PlanPrice(0, "key"));
        assertThrows(IllegalArgumentException.class, () -> new PlanPrice(1, " "));
    }

    @Test
    void a_catalog_is_refused_without_currency() {
        assertThrows(IllegalArgumentException.class, () -> new PlanCatalog(" ", "free", List.of(free())));
    }

    @Test
    void lowestAbove_names_the_first_plan_with_a_higher_limit() {
        Plan first = free();
        Plan second = plan("second", PRICE, 2, false);
        Plan third = plan("third", PRICE, 10, false);
        PlanCatalog catalog = catalog(first, second, third);

        assertEquals("third", catalog.lowestAbove(first, PlanLimit.SPELL_BOOKS).orElseThrow().id());
        assertTrue(catalog.lowestAbove(third, PlanLimit.SPELL_BOOKS).isEmpty());
    }

    @Test
    void lowestWith_names_the_first_plan_that_has_the_feature() {
        Plan first = free();
        Plan second = plan("second", PRICE, 2, false);
        Plan third = plan("third", PRICE, 10, true);
        PlanCatalog catalog = catalog(first, second, third);

        assertEquals("third", catalog.lowestWith(first, Feature.VIDEO).orElseThrow().id());
        assertTrue(catalog.lowestWith(third, Feature.VIDEO).isEmpty());
    }

    @Test
    void enums_are_found_by_their_key() {
        assertEquals(PlanLimit.SPELL_BOOKS, PlanLimit.fromKey("spellBooks").orElseThrow());
        assertEquals(PlanLimit.Scope.ADVENTURE, PlanLimit.NPCS_PER_ADVENTURE.scope());
        assertEquals(PlanLimit.Scope.ACCOUNT, PlanLimit.MAGIC_ITEMS.scope());
        assertEquals(Feature.Reach.MASTER, Feature.fromKey("video").orElseThrow().reach());
        assertEquals(Feature.Reach.SHAREABLE, Feature.CHARACTER_PICTURES.reach());
        assertEquals(Feature.Reach.ACCOUNT, Feature.EARLY_ACCESS.reach());
        assertEquals(PlanBadge.LEGEND, PlanBadge.fromKey("legend").orElseThrow());
        assertEquals(BillingInterval.YEARLY, BillingInterval.fromKey("yearly").orElseThrow());
        assertTrue(BillingInterval.fromKey("weekly").isEmpty());
    }
}
