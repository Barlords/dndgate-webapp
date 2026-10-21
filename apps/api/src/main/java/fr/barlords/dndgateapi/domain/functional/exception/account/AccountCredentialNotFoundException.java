package fr.barlords.dndgateapi.domain.functional.exception.account;

public class AccountCredentialNotFoundException extends RuntimeException{
    public static String EXCEPTION_MESSAGE = "No account matches the provided username and password.";

    public AccountCredentialNotFoundException() {
        super(EXCEPTION_MESSAGE);
    }
}
