package fr.barlords.dndgateapi.domain.functional.exception.account;

import lombok.Getter;

import java.time.Instant;

public class AccountLockedException extends RuntimeException {

    public static String EXCEPTION_MESSAGE = "Too many failed login attempts, try again later.";

    @Getter
    private final Instant lockedUntil;

    public AccountLockedException(Instant lockedUntil) {
        super(EXCEPTION_MESSAGE);
        this.lockedUntil = lockedUntil;
    }

}
