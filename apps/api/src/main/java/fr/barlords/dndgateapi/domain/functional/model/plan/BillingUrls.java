package fr.barlords.dndgateapi.domain.functional.model.plan;

/** The pages of the web application the hosted pages of the payment provider send the customer back to. */
public record BillingUrls(String checkoutSuccess, String checkoutCancel, String portalReturn) {

    /** {@code /plans/success} after a payment, {@code /plans} when it is abandoned, {@code /account} after the portal. */
    public static BillingUrls of(String webBaseUrl) {
        String base = webBaseUrl.endsWith("/") ? webBaseUrl.substring(0, webBaseUrl.length() - 1) : webBaseUrl;
        return new BillingUrls(base + "/plans/success", base + "/plans", base + "/account");
    }
}
