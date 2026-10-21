package fr.barlords.dndgateapi.domain.functional.exception.account;

/**
 * The code is wrong, expired or already used, or the address is unknown: all these cases give the same answer,
 * so that nobody can find out which addresses are registered.
 */
public class InvalidVerificationCodeException extends RuntimeException {

    public static String EXCEPTION_MESSAGE = "The verification link is invalid or has expired.";

    public InvalidVerificationCodeException() {
        super(EXCEPTION_MESSAGE);
    }

}
