package fr.barlords.dndgateapi.domain.functional.model.plan;

/** The price of a plan for one billing interval, in cents, and the Stripe price it maps to. */
public record PlanPrice(int amount, String stripeLookupKey) {

    public PlanPrice {
        if (amount < 1) {
            throw new IllegalArgumentException("A price must be positive");
        }
        if (stripeLookupKey == null || stripeLookupKey.isBlank()) {
            throw new IllegalArgumentException("A price needs a Stripe lookup key");
        }
    }
}
