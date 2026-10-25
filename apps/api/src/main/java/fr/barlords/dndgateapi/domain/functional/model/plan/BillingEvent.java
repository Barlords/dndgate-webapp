package fr.barlords.dndgateapi.domain.functional.model.plan;

/**
 * A verified notification of the payment provider.
 *
 * @param customerId the customer the event is about, null when the event has none
 */
public record BillingEvent(String type, String customerId) {
}
