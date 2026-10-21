package fr.barlords.dndgateapi.domain.functional.exception.account;

public class TokenNotValidException extends RuntimeException{
    public static String EXCEPTION_MESSAGE = "Token is invalid";

    public TokenNotValidException() {
        super(EXCEPTION_MESSAGE);
    }
}
