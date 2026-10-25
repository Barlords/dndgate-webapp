package fr.barlords.dndgateapi.domain.functional.exception.plan;

/** The account never went to the checkout: the payment provider knows no customer for it. */
public class NoBillingAccountException extends RuntimeException {

    public NoBillingAccountException() {
        super("The account has no billing account yet: subscribe to a plan first");
    }
}
