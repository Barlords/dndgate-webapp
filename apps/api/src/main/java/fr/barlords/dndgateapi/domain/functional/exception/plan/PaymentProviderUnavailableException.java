package fr.barlords.dndgateapi.domain.functional.exception.plan;

/** The payment provider is not configured, does not answer, or refused the request. */
public class PaymentProviderUnavailableException extends RuntimeException {

    public PaymentProviderUnavailableException(String message) {
        super(message);
    }

    public PaymentProviderUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
