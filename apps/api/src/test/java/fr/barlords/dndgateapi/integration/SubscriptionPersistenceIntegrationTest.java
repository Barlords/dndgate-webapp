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
import java.util.List;
import java.util.Map;
import java.util.UUID;

class SubscriptionPersistenceIntegrationTest extends ApiIntegrationTestSupport {

    @Autowired
    SubscriptionPersistenceSpi subscriptions;

    @Autowired
    EntitlementApi entitlements;

    @Test
    void a_subscription_is_saved_and_found_by_account_and_by_customer() {
        TestAccount account = registerAndLogin();
        TestAccount other = registerAndLogin();
        Instant end = Instant.now().plus(30, ChronoUnit.DAYS).truncatedTo(ChronoUnit.MILLIS);
        String customer = "cus_" + UUID.randomUUID();

        subscriptions.save(Subscription.builder().accountId(account.id()).stripeCustomerId(customer)
                .updatedAt(Instant.now()).build());
        Subscription customerOnly = subscriptions.findByAccountId(account.id()).orElseThrow();
        Assertions.assertNull(customerOnly.getStatus());
        Assertions.assertNull(customerOnly.getStripeSubscriptionId());
        Assertions.assertFalse(customerOnly.isCancelAtPeriodEnd());
        Assertions.assertEquals("test-default", entitlements.planOf(account.id()).id());

        subscriptions.save(customerOnly.withStripeSubscriptionId("sub_" + UUID.randomUUID()).withPlanId("test-paid")
                .withInterval(BillingInterval.YEARLY).withStatus(SubscriptionStatus.PAST_DUE)
                .withCurrentPeriodEnd(end).withCancelAtPeriodEnd(true));

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

        Map<UUID, Subscription> byIds = subscriptions.findByAccountIds(List.of(account.id(), other.id()));
        Assertions.assertEquals(1, byIds.size());
        Assertions.assertEquals(customer, byIds.get(account.id()).getStripeCustomerId());
        Assertions.assertTrue(subscriptions.findByAccountIds(List.of()).isEmpty());
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
