package fr.barlords.dndgateapi.domain.functional.model.plan;

import java.util.UUID;

/** What the payment provider needs to open a hosted payment page for an account. */
public record CheckoutRequest(
        String customerId,
        UUID accountId,
        String priceLookupKey,
        String successUrl,
        String cancelUrl) {
}
