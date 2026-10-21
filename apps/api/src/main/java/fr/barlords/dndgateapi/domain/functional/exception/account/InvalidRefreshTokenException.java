package fr.barlords.dndgateapi.domain.functional.exception.account;

public class InvalidRefreshTokenException extends RuntimeException {

    public static String EXCEPTION_MESSAGE = "The refresh token is invalid, expired or already used.";

    public InvalidRefreshTokenException() {
        super(EXCEPTION_MESSAGE);
    }

}
