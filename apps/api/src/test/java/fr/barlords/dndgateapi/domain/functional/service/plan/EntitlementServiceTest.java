package fr.barlords.dndgateapi.domain.functional.service.plan;

import fr.barlords.dndgateapi.domain.functional.exception.plan.FeatureNotIncludedException;
import fr.barlords.dndgateapi.domain.functional.exception.plan.PlanLimitReachedException;
import fr.barlords.dndgateapi.domain.functional.model.adventure.Adventure;
import fr.barlords.dndgateapi.domain.functional.model.plan.BillingInterval;
import fr.barlords.dndgateapi.domain.functional.model.plan.Feature;
import fr.barlords.dndgateapi.domain.functional.model.plan.Plan;
import fr.barlords.dndgateapi.domain.functional.model.plan.PlanBadge;
import fr.barlords.dndgateapi.domain.functional.model.plan.PlanCatalog;
import fr.barlords.dndgateapi.domain.functional.model.plan.PlanLimit;
import fr.barlords.dndgateapi.domain.functional.model.plan.PlanPrice;
import fr.barlords.dndgateapi.domain.functional.model.plan.Subscription;
import fr.barlords.dndgateapi.domain.functional.model.plan.SubscriptionStatus;
import fr.barlords.dndgateapi.domain.port.out.SubscriptionPersistenceSpi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mockito;

import java.time.Instant;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EntitlementServiceTest {

    private static final Map<BillingInterval, PlanPrice> PRICE =
            Map.of(BillingInterval.MONTHLY, new PlanPrice(100, "paid_monthly"));

    private final SubscriptionPersistenceSpi subscriptions = Mockito.mock(SubscriptionPersistenceSpi.class);
    private EntitlementService service;
    private final UUID account = UUID.randomUUID();
    private final UUID master = UUID.randomUUID();

    private static Plan plan(String id, boolean paid, int spellBooks, Set<Feature> features, PlanBadge badge) {
        Map<PlanLimit, Integer> limits = new EnumMap<>(PlanLimit.class);
        for (PlanLimit limit : PlanLimit.values()) {
            limits.put(limit, limit == PlanLimit.SPELL_BOOKS ? spellBooks : 5);
        }
        Map<Feature, Boolean> flags = new EnumMap<>(Feature.class);
        for (Feature feature : Feature.values()) {
            flags.put(feature, features.contains(feature));
        }
        return new Plan(id, false, paid ? PRICE : Map.of(), limits, flags, badge);
    }

    @BeforeEach
    void setUp() {
        PlanCatalog catalog = new PlanCatalog("EUR", "free", List.of(
                plan("free", false, 2, EnumSet.noneOf(Feature.class), null),
                plan("mid", true, 5, EnumSet.of(Feature.CHARACTER_PICTURES), null),
                plan("top", true, 5, EnumSet.allOf(Feature.class), PlanBadge.LEGEND)));
        service = new EntitlementService(subscriptions, catalog);
    }

    private void subscribe(UUID accountId, String planId, SubscriptionStatus status) {
        Mockito.when(subscriptions.findByAccountId(accountId)).thenReturn(Optional.of(Subscription.builder()
                .accountId(accountId).stripeCustomerId("cus_" + accountId).planId(planId).status(status)
                .updatedAt(Instant.now()).build()));
    }

    private Adventure adventureOf(UUID owner) {
        return Adventure.builder().ownerId(owner).build();
    }

    @Test
    void an_account_without_subscription_has_the_default_plan() {
        Mockito.when(subscriptions.findByAccountId(account)).thenReturn(Optional.empty());

        assertEquals("free", service.planOf(account).id());
    }

    @Test
    void a_customer_row_without_subscription_status_has_the_default_plan() {
        Mockito.when(subscriptions.findByAccountId(account)).thenReturn(Optional.of(Subscription.builder()
                .accountId(account).stripeCustomerId("cus_1").updatedAt(Instant.now()).build()));

        assertEquals("free", service.planOf(account).id());
    }

    @ParameterizedTest
    @EnumSource(value = SubscriptionStatus.class, names = {"ACTIVE", "TRIALING", "PAST_DUE"})
    void an_active_trialing_or_past_due_subscription_gives_its_plan(SubscriptionStatus status) {
        subscribe(account, "mid", status);

        assertEquals("mid", service.planOf(account).id());
    }

    @ParameterizedTest
    @EnumSource(value = SubscriptionStatus.class, names = {"CANCELED", "UNPAID", "INCOMPLETE", "INCOMPLETE_EXPIRED", "PAUSED"})
    void a_canceled_unpaid_incomplete_or_paused_subscription_gives_the_default_plan(SubscriptionStatus status) {
        subscribe(account, "mid", status);

        assertEquals("free", service.planOf(account).id());
    }

    @Test
    void an_unknown_plan_id_gives_the_default_plan() {
        subscribe(account, "retired", SubscriptionStatus.ACTIVE);

        assertEquals("free", service.planOf(account).id());
    }

    @Test
    void a_vacant_master_seat_gives_the_default_plan() {
        subscribe(master, "top", SubscriptionStatus.ACTIVE);

        assertEquals("top", service.masterPlanOf(adventureOf(master)).id());
        assertEquals("free", service.masterPlanOf(adventureOf(master).withMasterVacantSince(Instant.now())).id());
    }

    @Test
    void the_limit_refuses_at_the_maximum_and_names_the_upgrade() {
        Mockito.when(subscriptions.findByAccountId(account)).thenReturn(Optional.empty());

        PlanLimitReachedException e = assertThrows(PlanLimitReachedException.class,
                () -> service.verifyAccountLimit(account, PlanLimit.SPELL_BOOKS, 2));
        assertEquals(PlanLimit.SPELL_BOOKS, e.getLimit());
        assertEquals(2, e.getMax());
        assertEquals("free", e.getPlanId());
        assertEquals("mid", e.getUpgradePlanId());
        service.verifyAccountLimit(account, PlanLimit.SPELL_BOOKS, 1);
    }

    @Test
    void the_adventure_limit_follows_the_plan_of_the_master() {
        subscribe(master, "mid", SubscriptionStatus.ACTIVE);
        Adventure adventure = adventureOf(master);

        service.verifyAdventureLimit(adventure, PlanLimit.SPELL_BOOKS, 4);
        PlanLimitReachedException e = assertThrows(PlanLimitReachedException.class,
                () -> service.verifyAdventureLimit(adventure, PlanLimit.SPELL_BOOKS, 5));
        assertEquals("mid", e.getPlanId());
        assertEquals(5, e.getMax());
        assertNull(e.getUpgradePlanId());
    }

    @Test
    void a_shareable_feature_comes_from_the_master_only_with_an_equipped_table() {
        Mockito.when(subscriptions.findByAccountId(account)).thenReturn(Optional.empty());
        Adventure withMid = adventureOf(master);
        subscribe(master, "mid", SubscriptionStatus.ACTIVE);

        assertFalse(service.allows(account, withMid, Feature.CHARACTER_PICTURES));
        assertFalse(service.allows(account, null, Feature.CHARACTER_PICTURES));

        subscribe(master, "top", SubscriptionStatus.ACTIVE);
        assertTrue(service.allows(account, withMid, Feature.CHARACTER_PICTURES));
        assertFalse(service.allows(account, null, Feature.CHARACTER_PICTURES));

        subscribe(account, "mid", SubscriptionStatus.ACTIVE);
        assertTrue(service.allows(account, null, Feature.CHARACTER_PICTURES));
    }

    @Test
    void a_master_feature_ignores_the_plan_of_the_caller() {
        subscribe(account, "top", SubscriptionStatus.ACTIVE);
        Mockito.when(subscriptions.findByAccountId(master)).thenReturn(Optional.empty());

        assertFalse(service.allows(account, adventureOf(master), Feature.VIDEO));
        assertFalse(service.allows(account, null, Feature.VIDEO));

        subscribe(master, "top", SubscriptionStatus.ACTIVE);
        Mockito.when(subscriptions.findByAccountId(account)).thenReturn(Optional.empty());
        assertTrue(service.allows(account, adventureOf(master), Feature.VIDEO));
    }

    @Test
    void an_account_feature_follows_the_plan_of_the_caller_only() {
        Mockito.when(subscriptions.findByAccountId(account)).thenReturn(Optional.empty());
        subscribe(master, "top", SubscriptionStatus.ACTIVE);

        assertFalse(service.allows(account, adventureOf(master), Feature.PRIORITY_SUPPORT));
        subscribe(account, "top", SubscriptionStatus.ACTIVE);
        assertTrue(service.allows(account, null, Feature.PRIORITY_SUPPORT));
    }

    @Test
    void a_missing_feature_is_refused_and_names_the_upgrade() {
        Mockito.when(subscriptions.findByAccountId(account)).thenReturn(Optional.empty());

        FeatureNotIncludedException e = assertThrows(FeatureNotIncludedException.class,
                () -> service.verifyFeature(account, null, Feature.VIDEO));
        assertEquals(Feature.VIDEO, e.getFeature());
        assertEquals("free", e.getPlanId());
        assertEquals("top", e.getUpgradePlanId());

        subscribe(account, "top", SubscriptionStatus.ACTIVE);
        service.verifyFeature(account, null, Feature.PRIORITY_SUPPORT);
    }

    @Test
    void a_refused_master_feature_names_the_plan_of_the_master_and_its_upgrade() {
        subscribe(account, "top", SubscriptionStatus.ACTIVE);
        subscribe(master, "mid", SubscriptionStatus.ACTIVE);

        FeatureNotIncludedException e = assertThrows(FeatureNotIncludedException.class,
                () -> service.verifyFeature(account, adventureOf(master), Feature.VIDEO));

        assertEquals("mid", e.getPlanId());
        assertEquals("top", e.getUpgradePlanId());
    }

    @Test
    void a_refused_shareable_feature_names_the_plan_of_the_caller_and_its_upgrade() {
        Mockito.when(subscriptions.findByAccountId(account)).thenReturn(Optional.empty());
        subscribe(master, "free", SubscriptionStatus.ACTIVE);

        FeatureNotIncludedException e = assertThrows(FeatureNotIncludedException.class,
                () -> service.verifyFeature(account, adventureOf(master), Feature.CHARACTER_PICTURES));

        assertEquals("free", e.getPlanId());
        assertEquals("mid", e.getUpgradePlanId());
    }

    @Test
    void available_features_lists_what_works_in_the_adventure() {
        Mockito.when(subscriptions.findByAccountId(account)).thenReturn(Optional.empty());
        subscribe(master, "top", SubscriptionStatus.ACTIVE);

        assertEquals(EnumSet.of(Feature.CHARACTER_PICTURES, Feature.VIDEO),
                service.availableFeatures(account, adventureOf(master)));

        subscribe(master, "mid", SubscriptionStatus.ACTIVE);
        assertEquals(EnumSet.noneOf(Feature.class), service.availableFeatures(account, adventureOf(master)));
    }

    @Test
    void available_features_leaves_out_the_account_features_of_the_caller() {
        subscribe(account, "top", SubscriptionStatus.ACTIVE);
        subscribe(master, "mid", SubscriptionStatus.ACTIVE);

        assertEquals(EnumSet.of(Feature.CHARACTER_PICTURES), service.availableFeatures(account, adventureOf(master)));
    }

    @Test
    void the_badge_follows_the_effective_plan() {
        Mockito.when(subscriptions.findByAccountId(account)).thenReturn(Optional.empty());
        assertEquals(Optional.empty(), service.badgeOf(account));

        subscribe(account, "top", SubscriptionStatus.ACTIVE);
        assertEquals(Optional.of(PlanBadge.LEGEND), service.badgeOf(account));

        subscribe(account, "top", SubscriptionStatus.CANCELED);
        assertEquals(Optional.empty(), service.badgeOf(account));
    }

    @Test
    void stripe_statuses_are_read_and_classified() {
        assertEquals(Optional.of(SubscriptionStatus.PAST_DUE), SubscriptionStatus.fromStripe("past_due"));
        assertEquals(Optional.of(SubscriptionStatus.INCOMPLETE_EXPIRED), SubscriptionStatus.fromStripe("incomplete_expired"));
        assertEquals(Optional.empty(), SubscriptionStatus.fromStripe("surprising"));
        assertEquals(Optional.empty(), SubscriptionStatus.fromStripe(null));
        assertTrue(SubscriptionStatus.INCOMPLETE.isOngoing());
        assertFalse(SubscriptionStatus.INCOMPLETE.grantsPlan());
        assertTrue(SubscriptionStatus.TRIALING.isOngoing());
        assertFalse(SubscriptionStatus.CANCELED.isOngoing());
        assertFalse(SubscriptionStatus.PAUSED.isOngoing());
    }
}
