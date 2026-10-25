package fr.barlords.dndgateapi.domain.functional.exception.plan;

/** A webhook call whose signature is missing or does not match. */
public class InvalidWebhookSignatureException extends RuntimeException {

    public InvalidWebhookSignatureException(String message) {
        super(message);
    }

    public InvalidWebhookSignatureException(String message, Throwable cause) {
        super(message, cause);
    }
}
