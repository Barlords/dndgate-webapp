package fr.barlords.dndgateapi.domain.functional.service.plan;

import fr.barlords.dndgateapi.domain.functional.exception.plan.NoBillingAccountException;
import fr.barlords.dndgateapi.domain.functional.exception.plan.PaymentProviderUnavailableException;
import fr.barlords.dndgateapi.domain.functional.exception.plan.PlanNotPurchasableException;
import fr.barlords.dndgateapi.domain.functional.exception.plan.SubscriptionAlreadyActiveException;
import fr.barlords.dndgateapi.domain.functional.model.plan.BillingEvent;
import fr.barlords.dndgateapi.domain.functional.model.plan.BillingInterval;
import fr.barlords.dndgateapi.domain.functional.model.plan.BillingUrls;
import fr.barlords.dndgateapi.domain.functional.model.plan.CheckoutRequest;
import fr.barlords.dndgateapi.domain.functional.model.plan.LimitUsage;
import fr.barlords.dndgateapi.domain.functional.model.plan.Plan;
import fr.barlords.dndgateapi.domain.functional.model.plan.PlanCatalog;
import fr.barlords.dndgateapi.domain.functional.model.plan.PlanLimit;
import fr.barlords.dndgateapi.domain.functional.model.plan.PlanPrice;
import fr.barlords.dndgateapi.domain.functional.model.plan.ProviderSubscription;
import fr.barlords.dndgateapi.domain.functional.model.plan.Subscription;
import fr.barlords.dndgateapi.domain.functional.model.plan.SubscriptionOverview;
import fr.barlords.dndgateapi.domain.functional.service.CurrentAccountService;
import fr.barlords.dndgateapi.domain.port.in.plan.BillingApi;
import fr.barlords.dndgateapi.domain.port.in.plan.EntitlementApi;
import fr.barlords.dndgateapi.domain.port.out.AdventurePersistenceSpi;
import fr.barlords.dndgateapi.domain.port.out.GameCharacterPersistenceSpi;
import fr.barlords.dndgateapi.domain.port.out.MagicItemPersistenceSpi;
import fr.barlords.dndgateapi.domain.port.out.PaymentProviderSpi;
import fr.barlords.dndgateapi.domain.port.out.SpellBookPersistenceSpi;
import fr.barlords.dndgateapi.domain.port.out.SubscriptionPersistenceSpi;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.Clock;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Not transactional on purpose: no call to the payment provider runs inside a database transaction. Each write is
 * one statement of the persistence adapter.
 */
@Slf4j
@RequiredArgsConstructor
public class BillingService implements BillingApi {

    /** The notifications that may change a subscription; the others are acknowledged without effect. */
    static final Set<String> SYNCED_EVENTS = Set.of("checkout.session.completed", "customer.subscription.created",
            "customer.subscription.updated", "customer.subscription.deleted");

    private final PaymentProviderSpi provider;
    private final SubscriptionPersistenceSpi subscriptions;
    private final SubscriptionSynchronizer synchronizer;
    private final EntitlementApi entitlementApi;
    private final PlanCatalog catalog;
    private final CurrentAccountService currentAccountService;
    private final AdventurePersistenceSpi adventures;
    private final GameCharacterPersistenceSpi gameCharacters;
    private final SpellBookPersistenceSpi spellBooks;
    private final MagicItemPersistenceSpi magicItems;
    private final BillingUrls urls;
    private final Clock clock;

    @Override
    public SubscriptionOverview mine(UUID accountId) {
        Plan plan = entitlementApi.planOf(accountId);
        Map<PlanLimit, LimitUsage> usage = new EnumMap<>(PlanLimit.class);
        for (PlanLimit limit : PlanLimit.values()) {
            if (limit.scope() == PlanLimit.Scope.ACCOUNT) {
                usage.put(limit, new LimitUsage(used(limit, accountId), plan.limit(limit)));
            }
        }
        Subscription subscription = subscriptions.findByAccountId(accountId).orElse(null);
        return new SubscriptionOverview(plan, subscription, usage, provider.isEnabled());
    }

    /** The same counts as the checks made when something is created. */
    private long used(PlanLimit limit, UUID accountId) {
        return switch (limit) {
            case ACTIVE_ADVENTURES_AS_MASTER -> adventures.countActiveByOwnerId(accountId);
            case CHARACTERS_WITHOUT_ADVENTURE -> gameCharacters.countByOwnerIdWithoutAdventure(accountId);
            case SPELL_BOOKS -> spellBooks.countByOwnerId(accountId);
            case MAGIC_ITEMS -> magicItems.countByOwnerId(accountId);
            default -> throw new IllegalStateException("Not an account limit: " + limit);
        };
    }

    @Override
    public String startCheckout(UUID accountId, String planId, BillingInterval interval) {
        requireEnabled();
        // a free plan has no price at all (PlanCatalog), so it is refused here too
        PlanPrice price = catalog.find(planId)
                .map(plan -> interval == null ? null : plan.prices().get(interval))
                .orElseThrow(() -> new PlanNotPurchasableException(planId, interval));

        Subscription row = subscriptions.findByAccountId(accountId).orElseGet(() -> createCustomer(accountId));
        String customerId = row.getStripeCustomerId();
        // Stripe, not the row: the row only learns of a subscription when its webhook arrives
        if (provider.findSubscriptions(customerId).stream().anyMatch(found -> found.status().isOngoing())) {
            throw new SubscriptionAlreadyActiveException();
        }
        return provider.createCheckoutSession(new CheckoutRequest(customerId, accountId, price.stripeLookupKey(),
                urls.checkoutSuccess(), urls.checkoutCancel()));
    }

    private Subscription createCustomer(UUID accountId) {
        String email = currentAccountService.getAccountById(accountId).getEmail();
        String customerId = provider.createCustomer(accountId, email);
        Subscription recorded = subscriptions.createCustomerIfAbsent(accountId, customerId, clock.instant());
        if (!recorded.getStripeCustomerId().equals(customerId)) {
            log.warn("A concurrent checkout of the account {} recorded its Stripe customer first: "
                    + "the Stripe customer {} is not used", accountId, customerId);
        }
        return recorded;
    }

    @Override
    public String openPortal(UUID accountId) {
        requireEnabled();
        Subscription row = subscriptions.findByAccountId(accountId).orElseThrow(NoBillingAccountException::new);
        return provider.createPortalSession(row.getStripeCustomerId(), urls.portalReturn());
    }

    @Override
    public void handleWebhook(String payload, String signatureHeader) {
        requireEnabled();
        BillingEvent event = provider.parseWebhookEvent(payload, signatureHeader);
        if (!SYNCED_EVENTS.contains(event.type())) {
            return;
        }
        Optional<Subscription> row = Optional.ofNullable(event.customerId()).flatMap(subscriptions::findByStripeCustomerId);
        if (row.isEmpty()) {
            log.warn("The Stripe event {} is about the customer {}, who has no account here: it is ignored",
                    event.type(), event.customerId());
            return;
        }
        synchronizer.sync(row.get());
    }

    @Override
    public void cancelForAnonymizedAccount(UUID accountId) {
        Optional<Subscription> row = subscriptions.findByAccountId(accountId);
        if (row.isEmpty()) {
            return;
        }
        requireEnabled();
        String customerId = row.get().getStripeCustomerId();
        // First, so that no page opened before the deletion can still be paid: a page paid meanwhile has created its
        // subscription, which the reading below finds and ends
        provider.expireOpenCheckoutSessions(customerId);
        for (ProviderSubscription found : provider.findSubscriptions(customerId)) {
            if (found.status().isOngoing()) {
                provider.cancelNow(found.id());
            }
        }
    }

    private void requireEnabled() {
        if (!provider.isEnabled()) {
            throw new PaymentProviderUnavailableException("Billing is disabled");
        }
    }
}
