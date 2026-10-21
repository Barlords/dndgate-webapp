package fr.barlords.dndgateapi.domain.functional.exception.adventure;

import lombok.Getter;

import java.time.Instant;

public class InvitationRateLimitException extends RuntimeException {

    public static final String SENDER_MESSAGE = "Too many invitations sent, try again later.";

    public static final String RECIPIENT_MESSAGE = "This player was already invited to this adventure recently, try again later.";

    @Getter
    private final Instant retryAt;

    private InvitationRateLimitException(String message, Instant retryAt) {
        super(message);
        this.retryAt = retryAt;
    }

    public static InvitationRateLimitException senderLimit(Instant retryAt) {
        return new InvitationRateLimitException(SENDER_MESSAGE, retryAt);
    }

    public static InvitationRateLimitException recipientLimit(Instant retryAt) {
        return new InvitationRateLimitException(RECIPIENT_MESSAGE, retryAt);
    }

}
