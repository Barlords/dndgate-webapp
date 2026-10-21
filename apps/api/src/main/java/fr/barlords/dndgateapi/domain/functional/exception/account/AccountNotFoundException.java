package fr.barlords.dndgateapi.domain.functional.exception.account;

public class AccountNotFoundException extends RuntimeException{
    public static String EXCEPTION_MESSAGE = "Account not found";

    public AccountNotFoundException() {
        super(EXCEPTION_MESSAGE);
    }
}
