package fr.barlords.dndgateapi.domain.functional.exception.plan;

/** The account already has a subscription that is not over: it is changed in the portal, not bought again. */
public class SubscriptionAlreadyActiveException extends RuntimeException {

    public SubscriptionAlreadyActiveException() {
        super("The account already has an ongoing subscription: change it in the billing portal");
    }
}
