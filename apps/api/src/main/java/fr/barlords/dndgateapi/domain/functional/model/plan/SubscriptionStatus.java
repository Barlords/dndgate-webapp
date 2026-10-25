package fr.barlords.dndgateapi.domain.functional.model.plan;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

/** The state of a Stripe subscription; only some states give the paid plan. */
public enum SubscriptionStatus {
    ACTIVE, TRIALING, PAST_DUE, CANCELED, UNPAID, INCOMPLETE, INCOMPLETE_EXPIRED, PAUSED;

    /** Whether the plan of the subscription applies (a late payment keeps it while Stripe retries). */
    public boolean grantsPlan() {
        return this == ACTIVE || this == TRIALING || this == PAST_DUE;
    }

    /** Whether the subscription still blocks the creation of another one. */
    public boolean isOngoing() {
        return grantsPlan() || this == INCOMPLETE;
    }

    /** Reads the status string of Stripe, e.g. {@code past_due}. */
    public static Optional<SubscriptionStatus> fromStripe(String value) {
        if (value == null) {
            return Optional.empty();
        }
        String name = value.toUpperCase(Locale.ROOT);
        return Arrays.stream(values()).filter(status -> status.name().equals(name)).findFirst();
    }
}
