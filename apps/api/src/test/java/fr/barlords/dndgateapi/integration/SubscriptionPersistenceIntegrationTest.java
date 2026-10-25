package fr.barlords.dndgateapi.integration;

import fr.barlords.dndgateapi.domain.functional.model.plan.BillingInterval;
import fr.barlords.dndgateapi.domain.functional.model.plan.Subscription;
import fr.barlords.dndgateapi.domain.functional.model.plan.SubscriptionStatus;
import fr.barlords.dndgateapi.domain.port.in.plan.EntitlementApi;
import fr.barlords.dndgateapi.domain.port.out.SubscriptionPersistenceSpi;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

class SubscriptionPersistenceIntegrationTest extends ApiIntegrationTestSupport {

    @Autowired
    SubscriptionPersistenceSpi subscriptions;

    @Autowired
    EntitlementApi entitlements;

    @Test
    void a_subscription_is_found_by_account_and_by_customer() {
        TestAccount account = registerAndLogin();
        TestAccount other = registerAndLogin();
        Instant end = Instant.now().plus(30, ChronoUnit.DAYS).truncatedTo(ChronoUnit.MILLIS);
        String customer = "cus_" + UUID.randomUUID();

        subscriptions.createCustomerIfAbsent(account.id(), customer, Instant.now());
        Subscription customerOnly = subscriptions.findByAccountId(account.id()).orElseThrow();
        Assertions.assertNull(customerOnly.getStatus());
        Assertions.assertNull(customerOnly.getStripeSubscriptionId());
        Assertions.assertFalse(customerOnly.isCancelAtPeriodEnd());
        Assertions.assertEquals("test-default", entitlements.planOf(account.id()).id());

        // written as a sync would leave it, every column read back below
        jdbc.update("update subscription set stripe_subscription_id = ?, plan_id = 'test-paid', billing_interval = 'YEARLY', "
                + "status = 'PAST_DUE', current_period_end = ?, cancel_at_period_end = true where account_id = ?",
                "sub_" + UUID.randomUUID(), java.sql.Timestamp.from(end), account.id());

        Subscription found = subscriptions.findByStripeCustomerId(customer).orElseThrow();
        Assertions.assertEquals(account.id(), found.getAccountId());
        Assertions.assertEquals("test-paid", found.getPlanId());
        Assertions.assertEquals(BillingInterval.YEARLY, found.getInterval());
        Assertions.assertEquals(SubscriptionStatus.PAST_DUE, found.getStatus());
        Assertions.assertEquals(end, found.getCurrentPeriodEnd());
        Assertions.assertTrue(found.isCancelAtPeriodEnd());
        Assertions.assertEquals("PAST_DUE", jdbc.queryForObject(
                "select status from subscription where account_id = ?", String.class, account.id()));
        Assertions.assertEquals("test-paid", entitlements.planOf(account.id()).id());

        Assertions.assertTrue(subscriptions.findByStripeCustomerId("cus_unknown").isEmpty());
        Assertions.assertTrue(subscriptions.findByAccountId(other.id()).isEmpty());

        subscriptions.deleteByAccountId(account.id());
        Assertions.assertTrue(subscriptions.findByAccountId(account.id()).isEmpty());
    }

    @Test
    void a_customer_is_recorded_only_when_the_account_has_no_row() {
        TestAccount account = registerAndLogin();
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);

        Subscription first = subscriptions.createCustomerIfAbsent(account.id(), "cus_first_" + account.id(), now);
        Assertions.assertEquals("cus_first_" + account.id(), first.getStripeCustomerId());
        Assertions.assertEquals(account.id(), first.getAccountId());
        Assertions.assertNull(first.getStatus());
        Assertions.assertEquals(now, first.getUpdatedAt());

        // a second (concurrent) first checkout keeps the row that is there
        Subscription second = subscriptions.createCustomerIfAbsent(account.id(), "cus_second_" + account.id(), now);
        Assertions.assertEquals("cus_first_" + account.id(), second.getStripeCustomerId());

        subscribe(account, "test-paid");
        Subscription kept = subscriptions.createCustomerIfAbsent(account.id(), "cus_third_" + account.id(), now);
        Assertions.assertEquals(SubscriptionStatus.ACTIVE, kept.getStatus());
        Assertions.assertEquals("test-paid", kept.getPlanId());
        Assertions.assertEquals(1, jdbc.queryForObject(
                "select count(*) from subscription where account_id = ?", Integer.class, account.id()));
    }

    @Test
    void the_conditional_update_only_changes_the_row_that_was_read() {
        TestAccount account = registerAndLogin();
        TestAccount withoutRow = registerAndLogin();
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        Subscription read = subscriptions.createCustomerIfAbsent(account.id(), "cus_cas_" + account.id(), now);
        Subscription next = read.withStripeSubscriptionId("sub_" + UUID.randomUUID()).withPlanId("test-paid")
                .withInterval(BillingInterval.MONTHLY).withStatus(SubscriptionStatus.ACTIVE)
                .withCurrentPeriodEnd(now.plus(30, ChronoUnit.DAYS)).withCancelAtPeriodEnd(true)
                .withUpdatedAt(now.plus(1, ChronoUnit.SECONDS));

        // a stale reading is refused and changes nothing
        Assertions.assertFalse(subscriptions.updateIfUnchanged(next, now.minus(1, ChronoUnit.SECONDS)));
        Assertions.assertNull(subscriptions.findByAccountId(account.id()).orElseThrow().getStatus());

        Assertions.assertTrue(subscriptions.updateIfUnchanged(next, read.getUpdatedAt()));
        Assertions.assertEquals(next, subscriptions.findByAccountId(account.id()).orElseThrow());
        Assertions.assertEquals("test-paid", entitlements.planOf(account.id()).id());

        // the reading it was based on is now outdated: a second write from it is refused
        Assertions.assertFalse(subscriptions.updateIfUnchanged(next.withStatus(SubscriptionStatus.CANCELED), read.getUpdatedAt()));
        Assertions.assertEquals(SubscriptionStatus.ACTIVE, subscriptions.findByAccountId(account.id()).orElseThrow().getStatus());

        // never a new row: an account without one (or whose row was deleted) stays without
        Assertions.assertFalse(subscriptions.updateIfUnchanged(next.withAccountId(withoutRow.id())
                .withStripeCustomerId("cus_none_" + withoutRow.id()).withStripeSubscriptionId(null), read.getUpdatedAt()));
        Assertions.assertTrue(subscriptions.findByAccountId(withoutRow.id()).isEmpty());
    }

    @Test
    void the_test_helper_replaces_the_subscription_of_the_account() {
        TestAccount account = registerAndLogin();

        subscribe(account, "test-tight");
        Assertions.assertEquals("test-tight", entitlements.planOf(account.id()).id());
        subscribe(account, "test-paid");
        Assertions.assertEquals("test-paid", entitlements.planOf(account.id()).id());
        Assertions.assertEquals("cus_test_" + account.id(),
                subscriptions.findByAccountId(account.id()).orElseThrow().getStripeCustomerId());
    }
}
