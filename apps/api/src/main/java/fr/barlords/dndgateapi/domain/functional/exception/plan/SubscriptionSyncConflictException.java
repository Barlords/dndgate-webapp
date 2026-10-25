package fr.barlords.dndgateapi.domain.functional.exception.plan;

/** The subscription row kept changing while it was being brought up to date: the notification must come again. */
public class SubscriptionSyncConflictException extends RuntimeException {

    public SubscriptionSyncConflictException(String customerId) {
        super("The subscription of the Stripe customer " + customerId + " kept changing during its update");
    }
}
