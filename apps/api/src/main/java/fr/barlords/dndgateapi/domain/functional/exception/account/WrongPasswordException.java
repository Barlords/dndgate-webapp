package fr.barlords.dndgateapi.domain.functional.exception.account;

public class WrongPasswordException extends RuntimeException {

    public static String EXCEPTION_MESSAGE = "The password is wrong";

    public WrongPasswordException() {
        super(EXCEPTION_MESSAGE);
    }

}
