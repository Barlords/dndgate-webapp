package fr.barlords.dndgateapi.client.controller;

import fr.barlords.dndgateapi.client.dto.plan.CheckoutSessionRequest;
import fr.barlords.dndgateapi.client.dto.plan.RedirectDto;
import fr.barlords.dndgateapi.client.service.AuthVerifierService;
import fr.barlords.dndgateapi.domain.functional.model.plan.BillingInterval;
import fr.barlords.dndgateapi.domain.port.in.plan.BillingApi;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.http.HttpStatus.CREATED;
import static org.springframework.http.HttpStatus.OK;

@Tag(name = "Billing")
@RestController
@RequiredArgsConstructor
@RequestMapping(path = "/billing")
public class BillingController {

    private final AuthVerifierService authVerifierService;

    private final BillingApi billingApi;

    @PostMapping("/checkout-session")
    @ResponseStatus(CREATED)
    @Operation(summary = "Open a Stripe checkout",
            description = "The URL of the Stripe page where the plan is paid. The plan only applies once Stripe has "
                    + "notified the API: read GET /accounts/me/subscription again on the return page. 409 when a "
                    + "subscription is ongoing (it is changed in the portal), 503 when billing is off.")
    public RedirectDto createCheckoutSession(@Valid @RequestBody CheckoutSessionRequest request) {
        // the pattern of the request only lets the keys of the intervals through
        BillingInterval interval = BillingInterval.fromKey(request.interval()).orElse(null);
        return new RedirectDto(billingApi.startCheckout(authVerifierService.currentAccountId(), request.planId(), interval));
    }

    @PostMapping("/portal-session")
    @ResponseStatus(CREATED)
    @Operation(summary = "Open the Stripe customer portal",
            description = "The URL of the Stripe page where the subscription is changed or cancelled and the invoices "
                    + "are read. 409 when the account never went to the checkout, 503 when billing is off.")
    public RedirectDto createPortalSession() {
        return new RedirectDto(billingApi.openPortal(authVerifierService.currentAccountId()));
    }

    /**
     * The body is taken as it came, without {@code @Valid}: Stripe signs these exact bytes, so nothing parses or
     * rewrites them before the payment provider has verified the signature.
     */
    @PostMapping("/stripe-webhook")
    @ResponseStatus(OK)
    @Operation(summary = "Receive a Stripe event",
            description = "Called by Stripe only, authenticated by the Stripe-Signature header (no token). "
                    + "400 when the signature is missing or wrong.")
    public void receiveStripeEvent(@RequestBody String payload, @RequestHeader("Stripe-Signature") String signature) {
        billingApi.handleWebhook(payload, signature);
    }
}
