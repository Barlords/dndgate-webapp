package fr.barlords.dndgateapi.domain.functional.exception;

import lombok.Getter;

import java.time.Instant;

public class MessageRateLimitException extends RuntimeException {

    public static final String EXCEPTION_MESSAGE = "Too many messages sent, try again later.";

    @Getter
    private final Instant retryAt;

    public MessageRateLimitException(Instant retryAt) {
        super(EXCEPTION_MESSAGE);
        this.retryAt = retryAt;
    }

}
