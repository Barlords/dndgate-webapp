package fr.barlords.dndgateapi.domain.functional.service.plan;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import fr.barlords.dndgateapi.domain.functional.exception.plan.NoBillingAccountException;
import fr.barlords.dndgateapi.domain.functional.exception.plan.PaymentProviderUnavailableException;
import fr.barlords.dndgateapi.domain.functional.exception.plan.PlanNotPurchasableException;
import fr.barlords.dndgateapi.domain.functional.exception.plan.SubscriptionAlreadyActiveException;
import fr.barlords.dndgateapi.domain.functional.exception.plan.SubscriptionSyncConflictException;
import fr.barlords.dndgateapi.domain.functional.model.account.Account;
import fr.barlords.dndgateapi.domain.functional.model.plan.BillingEvent;
import fr.barlords.dndgateapi.domain.functional.model.plan.BillingInterval;
import fr.barlords.dndgateapi.domain.functional.model.plan.BillingUrls;
import fr.barlords.dndgateapi.domain.functional.model.plan.CheckoutRequest;
import fr.barlords.dndgateapi.domain.functional.model.plan.Feature;
import fr.barlords.dndgateapi.domain.functional.model.plan.LimitUsage;
import fr.barlords.dndgateapi.domain.functional.model.plan.Plan;
import fr.barlords.dndgateapi.domain.functional.model.plan.PlanCatalog;
import fr.barlords.dndgateapi.domain.functional.model.plan.PlanLimit;
import fr.barlords.dndgateapi.domain.functional.model.plan.PlanPrice;
import fr.barlords.dndgateapi.domain.functional.model.plan.ProviderSubscription;
import fr.barlords.dndgateapi.domain.functional.model.plan.Subscription;
import fr.barlords.dndgateapi.domain.functional.model.plan.SubscriptionOverview;
import fr.barlords.dndgateapi.domain.functional.model.plan.SubscriptionStatus;
import fr.barlords.dndgateapi.domain.functional.service.CurrentAccountService;
import fr.barlords.dndgateapi.domain.port.out.AdventurePersistenceSpi;
import fr.barlords.dndgateapi.domain.port.out.GameCharacterPersistenceSpi;
import fr.barlords.dndgateapi.domain.port.out.MagicItemPersistenceSpi;
import fr.barlords.dndgateapi.domain.port.out.PaymentProviderSpi;
import fr.barlords.dndgateapi.domain.port.out.SpellBookPersistenceSpi;
import fr.barlords.dndgateapi.domain.port.out.SubscriptionPersistenceSpi;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class BillingServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-25T10:00:00Z");
    private static final String CUSTOMER = "cus_1";
    private static final BillingUrls URLS = new BillingUrls("https://web/plans/success", "https://web/plans", "https://web/account");
    private static final String UPDATED = "customer.subscription.updated";

    private final PaymentProviderSpi provider = mock(PaymentProviderSpi.class);
    private final SubscriptionPersistenceSpi subscriptions = mock(SubscriptionPersistenceSpi.class);
    private final CurrentAccountService accounts = mock(CurrentAccountService.class);
    private final AdventurePersistenceSpi adventures = mock(AdventurePersistenceSpi.class);
    private final GameCharacterPersistenceSpi characters = mock(GameCharacterPersistenceSpi.class);
    private final SpellBookPersistenceSpi spellBooks = mock(SpellBookPersistenceSpi.class);
    private final MagicItemPersistenceSpi magicItems = mock(MagicItemPersistenceSpi.class);
    private final UUID accountId = UUID.randomUUID();

    private PlanCatalog catalog;
    private BillingService service;
    private ListAppender<ILoggingEvent> logs;

    private static Plan plan(String id, Map<BillingInterval, PlanPrice> prices, int spellBooks) {
        Map<PlanLimit, Integer> limits = new EnumMap<>(PlanLimit.class);
        for (PlanLimit limit : PlanLimit.values()) {
            limits.put(limit, limit == PlanLimit.SPELL_BOOKS ? spellBooks : 5);
        }
        Map<Feature, Boolean> features = new EnumMap<>(Feature.class);
        for (Feature feature : Feature.values()) {
            features.put(feature, false);
        }
        return new Plan(id, false, prices, limits, features, null);
    }

    @BeforeEach
    void setUp() {
        catalog = new PlanCatalog("EUR", "free", List.of(
                plan("free", Map.of(), 2),
                plan("mid", Map.of(BillingInterval.MONTHLY, new PlanPrice(100, "mid_monthly")), 10),
                plan("top", Map.of(BillingInterval.MONTHLY, new PlanPrice(500, "top_monthly"),
                        BillingInterval.YEARLY, new PlanPrice(5000, "top_yearly")), 50)));
        EntitlementService entitlements = new EntitlementService(subscriptions, catalog);
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        service = new BillingService(provider, subscriptions, new SubscriptionSynchronizer(provider, subscriptions, catalog, clock),
                entitlements, catalog, accounts, adventures, characters, spellBooks, magicItems, URLS, clock);
        when(provider.isEnabled()).thenReturn(true);
        when(subscriptions.updateIfUnchanged(any(), any())).thenReturn(true);
        when(accounts.getAccountById(accountId)).thenReturn(Account.builder().id(accountId).email("me@test.fr").build());

        logs = new ListAppender<>();
        logs.start();
        ((Logger) LoggerFactory.getLogger(SubscriptionSynchronizer.class)).addAppender(logs);
        ((Logger) LoggerFactory.getLogger(BillingService.class)).addAppender(logs);
    }

    @AfterEach
    void tearDown() {
        ((Logger) LoggerFactory.getLogger(SubscriptionSynchronizer.class)).detachAppender(logs);
        ((Logger) LoggerFactory.getLogger(BillingService.class)).detachAppender(logs);
        // a sync only updates the row it read, never with a merge that could write a stale state or re-insert a row
        verify(subscriptions, never()).save(any());
    }

    private Subscription row(String planId, SubscriptionStatus status) {
        return Subscription.builder().accountId(accountId).stripeCustomerId(CUSTOMER).planId(planId).status(status)
                .updatedAt(NOW.minus(1, ChronoUnit.DAYS)).build();
    }

    private void hasRow(Subscription row) {
        when(subscriptions.findByAccountId(accountId)).thenReturn(Optional.of(row));
        when(subscriptions.findByStripeCustomerId(row.getStripeCustomerId())).thenReturn(Optional.of(row));
    }

    private static ProviderSubscription atStripe(String id, String planId, SubscriptionStatus status, int daysLeft) {
        return new ProviderSubscription(id, planId, BillingInterval.MONTHLY, status,
                NOW.plus(daysLeft, ChronoUnit.DAYS), false);
    }

    private Subscription saved() {
        ArgumentCaptor<Subscription> captor = ArgumentCaptor.forClass(Subscription.class);
        verify(subscriptions).updateIfUnchanged(captor.capture(), any());
        return captor.getValue();
    }

    private List<ILoggingEvent> logsAt(Level level) {
        return logs.list.stream().filter(event -> event.getLevel() == level).toList();
    }

    // ================= my subscription =================

    @Test
    void my_subscription_shows_the_effective_plan_the_row_and_the_usage_of_the_account_limits() {
        Subscription row = row("mid", SubscriptionStatus.ACTIVE);
        hasRow(row);
        when(adventures.countActiveByOwnerId(accountId)).thenReturn(1L);
        when(characters.countByOwnerIdWithoutAdventure(accountId)).thenReturn(2L);
        when(spellBooks.countByOwnerId(accountId)).thenReturn(3L);
        when(magicItems.countByOwnerId(accountId)).thenReturn(4L);

        SubscriptionOverview overview = service.mine(accountId);

        assertEquals("mid", overview.plan().id());
        assertSame(row, overview.subscription());
        assertTrue(overview.billingEnabled());
        assertEquals(Map.of(
                PlanLimit.ACTIVE_ADVENTURES_AS_MASTER, new LimitUsage(1, 5),
                PlanLimit.CHARACTERS_WITHOUT_ADVENTURE, new LimitUsage(2, 5),
                PlanLimit.SPELL_BOOKS, new LimitUsage(3, 10),
                PlanLimit.MAGIC_ITEMS, new LimitUsage(4, 5)), overview.usage());
    }

    @Test
    void without_a_row_my_subscription_is_the_default_plan_and_says_whether_billing_works() {
        when(subscriptions.findByAccountId(accountId)).thenReturn(Optional.empty());
        when(provider.isEnabled()).thenReturn(false);

        SubscriptionOverview overview = service.mine(accountId);

        assertEquals("free", overview.plan().id());
        assertNull(overview.subscription());
        assertFalse(overview.billingEnabled());
        assertEquals(new LimitUsage(0, 2), overview.usage().get(PlanLimit.SPELL_BOOKS));
    }

    // ================= checkout =================

    @Test
    void checkout_is_unavailable_without_stripe() {
        when(provider.isEnabled()).thenReturn(false);

        assertThrows(PaymentProviderUnavailableException.class,
                () -> service.startCheckout(accountId, "mid", BillingInterval.MONTHLY));
        // checked before anything else: even a plan that cannot be bought answers that billing is off
        assertThrows(PaymentProviderUnavailableException.class,
                () -> service.startCheckout(accountId, "free", BillingInterval.MONTHLY));
        assertThrows(PaymentProviderUnavailableException.class, () -> service.openPortal(accountId));
        assertThrows(PaymentProviderUnavailableException.class, () -> service.handleWebhook("{}", "sig"));
        verifyNoInteractions(subscriptions);
        verify(provider, never()).createCustomer(any(), any());
        verify(provider, never()).parseWebhookEvent(any(), any());
    }

    @Test
    void a_free_or_unknown_plan_or_missing_interval_cannot_be_bought() {
        assertThrows(PlanNotPurchasableException.class, () -> service.startCheckout(accountId, "free", BillingInterval.MONTHLY));
        assertThrows(PlanNotPurchasableException.class, () -> service.startCheckout(accountId, "ghost", BillingInterval.MONTHLY));
        assertThrows(PlanNotPurchasableException.class, () -> service.startCheckout(accountId, "mid", BillingInterval.YEARLY));
        assertThrows(PlanNotPurchasableException.class, () -> service.startCheckout(accountId, "mid", null));
        verifyNoInteractions(subscriptions);
        verify(provider, never()).createCustomer(any(), any());
    }

    @Test
    void the_first_checkout_creates_the_customer_once() {
        when(subscriptions.findByAccountId(accountId)).thenReturn(Optional.empty());
        when(provider.createCustomer(accountId, "me@test.fr")).thenReturn(CUSTOMER);
        Subscription created = row(null, null);
        when(subscriptions.createCustomerIfAbsent(accountId, CUSTOMER, NOW)).thenReturn(created);
        when(provider.findSubscriptions(CUSTOMER)).thenReturn(List.of());
        when(provider.createCheckoutSession(any())).thenReturn("https://stripe/checkout");

        assertEquals("https://stripe/checkout", service.startCheckout(accountId, "mid", BillingInterval.MONTHLY));
        verify(subscriptions).createCustomerIfAbsent(accountId, CUSTOMER, NOW);

        // the second checkout finds the row: no second customer
        hasRow(created);
        service.startCheckout(accountId, "mid", BillingInterval.MONTHLY);
        verify(provider, times(1)).createCustomer(any(), any());
        verify(subscriptions, times(1)).createCustomerIfAbsent(any(), any(), any());
        verify(subscriptions, never()).updateIfUnchanged(any(), any());
        assertTrue(logsAt(Level.WARN).isEmpty());
    }

    @Test
    void a_concurrent_first_checkout_continues_with_the_customer_already_recorded() {
        when(subscriptions.findByAccountId(accountId)).thenReturn(Optional.empty());
        when(provider.createCustomer(accountId, "me@test.fr")).thenReturn("cus_orphan");
        // the other checkout recorded its customer first: the row of the account is that one
        when(subscriptions.createCustomerIfAbsent(accountId, "cus_orphan", NOW)).thenReturn(row(null, null));
        when(provider.findSubscriptions(CUSTOMER)).thenReturn(List.of());
        when(provider.createCheckoutSession(any())).thenReturn("https://stripe/checkout");

        service.startCheckout(accountId, "mid", BillingInterval.MONTHLY);

        ArgumentCaptor<CheckoutRequest> request = ArgumentCaptor.forClass(CheckoutRequest.class);
        verify(provider).createCheckoutSession(request.capture());
        assertEquals(CUSTOMER, request.getValue().customerId());
        verify(provider, never()).findSubscriptions("cus_orphan");
        assertTrue(logsAt(Level.WARN).stream().anyMatch(event -> event.getFormattedMessage().contains("cus_orphan")),
                "the unused Stripe customer is reported");
    }

    @Test
    void an_ongoing_subscription_at_stripe_refuses_a_new_checkout_even_without_local_row_status() {
        hasRow(row(null, null));
        for (SubscriptionStatus status : SubscriptionStatus.values()) {
            when(provider.findSubscriptions(CUSTOMER)).thenReturn(List.of(
                    atStripe("sub_old", "mid", SubscriptionStatus.CANCELED, -10),
                    atStripe("sub_1", "mid", status, 20)));
            when(provider.createCheckoutSession(any())).thenReturn("https://stripe/checkout");

            if (status.isOngoing()) {
                assertThrows(SubscriptionAlreadyActiveException.class,
                        () -> service.startCheckout(accountId, "top", BillingInterval.MONTHLY), status.name());
            } else {
                assertEquals("https://stripe/checkout", service.startCheckout(accountId, "top", BillingInterval.MONTHLY),
                        status.name());
            }
        }
        // ACTIVE, TRIALING, PAST_DUE and INCOMPLETE block; CANCELED, UNPAID, INCOMPLETE_EXPIRED and PAUSED do not
        verify(provider, times(4)).createCheckoutSession(any());
    }

    @Test
    void the_checkout_uses_the_lookup_key_of_the_interval_and_the_web_urls() {
        hasRow(row(null, null));
        when(provider.findSubscriptions(CUSTOMER)).thenReturn(List.of());
        when(provider.createCheckoutSession(any())).thenReturn("https://stripe/checkout");

        service.startCheckout(accountId, "top", BillingInterval.YEARLY);

        verify(provider).createCheckoutSession(new CheckoutRequest(CUSTOMER, accountId, "top_yearly",
                "https://web/plans/success", "https://web/plans"));
    }

    @Test
    void the_return_pages_are_pages_of_the_web_application() {
        BillingUrls expected = new BillingUrls("https://web/plans/success", "https://web/plans", "https://web/account");

        assertEquals(expected, BillingUrls.of("https://web"));
        assertEquals(expected, BillingUrls.of("https://web/"));
    }

    // ================= portal =================

    @Test
    void the_portal_needs_a_customer() {
        when(subscriptions.findByAccountId(accountId)).thenReturn(Optional.empty());
        assertThrows(NoBillingAccountException.class, () -> service.openPortal(accountId));
        verify(provider, never()).createPortalSession(anyString(), anyString());

        hasRow(row(null, null));
        when(provider.createPortalSession(CUSTOMER, "https://web/account")).thenReturn("https://stripe/portal");
        assertEquals("https://stripe/portal", service.openPortal(accountId));
    }

    // ================= webhook =================

    @Test
    void a_webhook_syncs_the_best_granting_subscription() {
        hasRow(row(null, null));
        when(provider.parseWebhookEvent("payload", "sig")).thenReturn(new BillingEvent(UPDATED, CUSTOMER));
        ProviderSubscription best = new ProviderSubscription("sub_top", "top", BillingInterval.YEARLY,
                SubscriptionStatus.PAST_DUE, NOW.plus(5, ChronoUnit.DAYS), true);
        when(provider.findSubscriptions(CUSTOMER)).thenReturn(List.of(
                atStripe("sub_mid_later", "mid", SubscriptionStatus.ACTIVE, 300),
                best,
                atStripe("sub_top_ended", "top", SubscriptionStatus.CANCELED, 400),
                atStripe("sub_unknown", "ghost", SubscriptionStatus.ACTIVE, 500)));

        service.handleWebhook("payload", "sig");

        Subscription saved = saved();
        assertEquals(accountId, saved.getAccountId());
        assertEquals(CUSTOMER, saved.getStripeCustomerId());
        assertEquals("sub_top", saved.getStripeSubscriptionId());
        assertEquals("top", saved.getPlanId());
        assertEquals(BillingInterval.YEARLY, saved.getInterval());
        assertEquals(SubscriptionStatus.PAST_DUE, saved.getStatus());
        assertEquals(NOW.plus(5, ChronoUnit.DAYS), saved.getCurrentPeriodEnd());
        assertTrue(saved.isCancelAtPeriodEnd());
        assertEquals(NOW, saved.getUpdatedAt());
    }

    @Test
    void between_two_granting_subscriptions_of_the_same_plan_the_latest_period_end_wins() {
        hasRow(row(null, null));
        when(provider.parseWebhookEvent("payload", "sig")).thenReturn(new BillingEvent("checkout.session.completed", CUSTOMER));
        when(provider.findSubscriptions(CUSTOMER)).thenReturn(List.of(
                atStripe("sub_a", "mid", SubscriptionStatus.ACTIVE, 10),
                atStripe("sub_b", "mid", SubscriptionStatus.TRIALING, 30),
                atStripe("sub_c", "mid", SubscriptionStatus.ACTIVE, 20)));

        service.handleWebhook("payload", "sig");

        assertEquals("sub_b", saved().getStripeSubscriptionId());
    }

    @Test
    void an_unknown_plan_id_is_kept_as_read_and_gives_the_default_plan() {
        hasRow(row(null, null));
        when(provider.parseWebhookEvent("payload", "sig")).thenReturn(new BillingEvent("customer.subscription.created", CUSTOMER));
        when(provider.findSubscriptions(CUSTOMER)).thenReturn(List.of(atStripe("sub_1", "ghost", SubscriptionStatus.ACTIVE, 10)));

        service.handleWebhook("payload", "sig");

        assertEquals("ghost", saved().getPlanId());
    }

    @Test
    void a_deleted_event_for_a_duplicate_keeps_the_remaining_active_subscription() {
        Subscription row = row("mid", SubscriptionStatus.ACTIVE).withStripeSubscriptionId("sub_kept")
                .withInterval(BillingInterval.MONTHLY).withCurrentPeriodEnd(NOW.plus(20, ChronoUnit.DAYS));
        hasRow(row);
        when(provider.parseWebhookEvent("payload", "sig")).thenReturn(new BillingEvent("customer.subscription.deleted", CUSTOMER));
        when(provider.findSubscriptions(CUSTOMER)).thenReturn(List.of(
                atStripe("sub_duplicate", "top", SubscriptionStatus.CANCELED, 25),
                atStripe("sub_kept", "mid", SubscriptionStatus.ACTIVE, 20)));

        service.handleWebhook("payload", "sig");

        // nothing changed for the account: the row is not even written
        verify(subscriptions, never()).updateIfUnchanged(any(), any());
    }

    @Test
    void when_nothing_grants_a_plan_the_most_recent_subscription_is_recorded() {
        hasRow(row("top", SubscriptionStatus.ACTIVE).withStripeSubscriptionId("sub_2"));
        when(provider.parseWebhookEvent("payload", "sig")).thenReturn(new BillingEvent("customer.subscription.deleted", CUSTOMER));
        when(provider.findSubscriptions(CUSTOMER)).thenReturn(List.of(
                atStripe("sub_1", "mid", SubscriptionStatus.CANCELED, -100),
                atStripe("sub_2", "top", SubscriptionStatus.CANCELED, -1),
                atStripe("sub_3", "top", SubscriptionStatus.INCOMPLETE_EXPIRED, -50)));

        service.handleWebhook("payload", "sig");

        Subscription saved = saved();
        assertEquals("sub_2", saved.getStripeSubscriptionId());
        assertEquals(SubscriptionStatus.CANCELED, saved.getStatus());
        assertEquals("top", saved.getPlanId());
    }

    @Test
    void a_customer_without_any_subscription_at_stripe_keeps_its_row() {
        hasRow(row(null, null));
        when(provider.parseWebhookEvent("payload", "sig")).thenReturn(new BillingEvent(UPDATED, CUSTOMER));
        when(provider.findSubscriptions(CUSTOMER)).thenReturn(List.of());

        service.handleWebhook("payload", "sig");

        verify(subscriptions, never()).updateIfUnchanged(any(), any());
    }

    @Test
    void the_same_event_twice_changes_nothing() {
        Subscription row = row(null, null);
        hasRow(row);
        when(provider.parseWebhookEvent("payload", "sig")).thenReturn(new BillingEvent(UPDATED, CUSTOMER));
        when(provider.findSubscriptions(CUSTOMER)).thenReturn(List.of(atStripe("sub_1", "mid", SubscriptionStatus.ACTIVE, 30)));

        service.handleWebhook("payload", "sig");
        Subscription first = saved();
        hasRow(first);
        service.handleWebhook("payload", "sig");

        // the second time the row already says what Stripe says: no second write
        verify(subscriptions, times(1)).updateIfUnchanged(any(), any());
        assertEquals("mid", first.getPlanId());
    }

    @Test
    void an_event_of_an_unknown_customer_or_type_does_nothing() {
        when(subscriptions.findByStripeCustomerId("cus_unknown")).thenReturn(Optional.empty());
        when(provider.parseWebhookEvent("unknown", "sig")).thenReturn(new BillingEvent(UPDATED, "cus_unknown"));
        when(provider.parseWebhookEvent("no-customer", "sig")).thenReturn(new BillingEvent(UPDATED, null));
        when(provider.parseWebhookEvent("other", "sig")).thenReturn(new BillingEvent("invoice.paid", CUSTOMER));

        service.handleWebhook("unknown", "sig");
        service.handleWebhook("no-customer", "sig");
        service.handleWebhook("other", "sig");

        verify(provider, never()).findSubscriptions(any());
        verify(subscriptions, never()).updateIfUnchanged(any(), any());
        verify(subscriptions, never()).findByStripeCustomerId(CUSTOMER);
        assertTrue(logsAt(Level.WARN).stream().anyMatch(event -> event.getFormattedMessage().contains("cus_unknown")),
                "the unknown customer is reported");
    }

    @Test
    void two_granting_subscriptions_are_logged() {
        hasRow(row(null, null));
        when(provider.parseWebhookEvent("payload", "sig")).thenReturn(new BillingEvent(UPDATED, CUSTOMER));
        when(provider.findSubscriptions(CUSTOMER)).thenReturn(List.of(
                atStripe("sub_a", "mid", SubscriptionStatus.ACTIVE, 10),
                atStripe("sub_b", "top", SubscriptionStatus.PAST_DUE, 30),
                atStripe("sub_c", "top", SubscriptionStatus.CANCELED, 30)));

        service.handleWebhook("payload", "sig");

        List<ILoggingEvent> errors = logsAt(Level.ERROR);
        assertEquals(1, errors.size());
        String message = errors.getFirst().getFormattedMessage();
        assertTrue(message.contains(CUSTOMER) && message.contains("sub_a") && message.contains("sub_b"), message);
        assertFalse(message.contains("sub_c"), message);
        assertEquals("sub_b", saved().getStripeSubscriptionId());
    }

    @Test
    void a_single_granting_subscription_is_not_an_error() {
        hasRow(row(null, null));
        when(provider.parseWebhookEvent("payload", "sig")).thenReturn(new BillingEvent(UPDATED, CUSTOMER));
        when(provider.findSubscriptions(CUSTOMER)).thenReturn(List.of(
                atStripe("sub_a", "mid", SubscriptionStatus.ACTIVE, 10),
                atStripe("sub_b", "top", SubscriptionStatus.CANCELED, 30)));

        service.handleWebhook("payload", "sig");

        assertTrue(logsAt(Level.ERROR).isEmpty());
    }

    // ================= concurrent syncs =================

    @Test
    void the_update_is_conditional_on_the_row_read_and_its_time_always_moves_forward() {
        // the row was written in this very instant: the next write still gets a later time
        Subscription row = row(null, null).withUpdatedAt(NOW);
        hasRow(row);
        when(provider.parseWebhookEvent("payload", "sig")).thenReturn(new BillingEvent(UPDATED, CUSTOMER));
        when(provider.findSubscriptions(CUSTOMER)).thenReturn(List.of(atStripe("sub_1", "mid", SubscriptionStatus.ACTIVE, 30)));

        service.handleWebhook("payload", "sig");

        ArgumentCaptor<Subscription> next = ArgumentCaptor.forClass(Subscription.class);
        verify(subscriptions).updateIfUnchanged(next.capture(), org.mockito.ArgumentMatchers.eq(NOW));
        assertEquals(NOW.plus(1, ChronoUnit.MICROS), next.getValue().getUpdatedAt());
    }

    @Test
    void a_row_changed_meanwhile_is_read_again_with_stripe_before_writing() {
        Subscription first = row(null, null);
        Subscription changed = row("mid", SubscriptionStatus.ACTIVE).withStripeSubscriptionId("sub_1")
                .withUpdatedAt(NOW.minus(1, ChronoUnit.HOURS));
        hasRow(first);
        when(provider.parseWebhookEvent("payload", "sig")).thenReturn(new BillingEvent("customer.subscription.deleted", CUSTOMER));
        // the first reading of Stripe is older than the write of the other event
        when(provider.findSubscriptions(CUSTOMER))
                .thenReturn(List.of(atStripe("sub_1", "mid", SubscriptionStatus.ACTIVE, 30)))
                .thenReturn(List.of(atStripe("sub_1", "mid", SubscriptionStatus.CANCELED, 30)));
        when(subscriptions.updateIfUnchanged(any(), org.mockito.ArgumentMatchers.eq(first.getUpdatedAt()))).thenReturn(false);
        when(subscriptions.findByAccountId(accountId)).thenReturn(Optional.of(changed));

        service.handleWebhook("payload", "sig");

        ArgumentCaptor<Subscription> next = ArgumentCaptor.forClass(Subscription.class);
        verify(subscriptions).updateIfUnchanged(next.capture(), org.mockito.ArgumentMatchers.eq(changed.getUpdatedAt()));
        assertEquals(SubscriptionStatus.CANCELED, next.getValue().getStatus());
        verify(provider, times(2)).findSubscriptions(CUSTOMER);
    }

    @Test
    void a_row_deleted_during_the_sync_is_not_written_again() {
        hasRow(row(null, null));
        when(provider.parseWebhookEvent("payload", "sig")).thenReturn(new BillingEvent("customer.subscription.deleted", CUSTOMER));
        when(provider.findSubscriptions(CUSTOMER)).thenReturn(List.of(atStripe("sub_1", "mid", SubscriptionStatus.CANCELED, 30)));
        when(subscriptions.updateIfUnchanged(any(), any())).thenReturn(false);
        when(subscriptions.findByAccountId(accountId)).thenReturn(Optional.empty());

        service.handleWebhook("payload", "sig");

        verify(subscriptions, times(1)).updateIfUnchanged(any(), any());
        verify(provider, times(1)).findSubscriptions(CUSTOMER);
    }

    @Test
    void a_sync_that_keeps_conflicting_fails_so_that_stripe_sends_the_event_again() {
        hasRow(row(null, null));
        when(provider.parseWebhookEvent("payload", "sig")).thenReturn(new BillingEvent(UPDATED, CUSTOMER));
        when(provider.findSubscriptions(CUSTOMER)).thenReturn(List.of(atStripe("sub_1", "mid", SubscriptionStatus.ACTIVE, 30)));
        when(subscriptions.updateIfUnchanged(any(), any())).thenReturn(false);

        assertThrows(SubscriptionSyncConflictException.class, () -> service.handleWebhook("payload", "sig"));
        verify(subscriptions, times(3)).updateIfUnchanged(any(), any());
    }

    // ---- account anonymization ----

    @Test
    void an_account_without_a_row_is_cancelled_nowhere() {
        service.cancelForAnonymizedAccount(accountId);

        verifyNoInteractions(provider);
        verify(subscriptions, never()).deleteByAccountId(any());
    }

    @Test
    void only_the_ongoing_subscriptions_of_the_customer_are_ended() {
        hasRow(row("mid", SubscriptionStatus.ACTIVE));
        when(provider.findSubscriptions(CUSTOMER)).thenReturn(List.of(
                atStripe("sub_active", "mid", SubscriptionStatus.ACTIVE, 10),
                atStripe("sub_canceled", "mid", SubscriptionStatus.CANCELED, -10),
                atStripe("sub_past_due", "mid", SubscriptionStatus.PAST_DUE, 5)));

        service.cancelForAnonymizedAccount(accountId);

        verify(provider).cancelNow("sub_active");
        verify(provider, never()).cancelNow("sub_canceled");
        verify(provider).cancelNow("sub_past_due");
    }

    @Test
    void a_row_with_billing_off_refuses_the_cancellation() {
        hasRow(row("mid", SubscriptionStatus.ACTIVE));
        when(provider.isEnabled()).thenReturn(false);

        assertThrows(PaymentProviderUnavailableException.class, () -> service.cancelForAnonymizedAccount(accountId));

        verify(provider, never()).cancelNow(anyString());
    }
}
