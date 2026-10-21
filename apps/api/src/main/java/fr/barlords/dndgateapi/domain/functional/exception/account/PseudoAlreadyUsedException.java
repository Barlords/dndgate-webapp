package fr.barlords.dndgateapi.domain.functional.exception.account;

public class PseudoAlreadyUsedException extends RuntimeException{
    public static String EXCEPTION_MESSAGE = "The nickname is already in use";

    public PseudoAlreadyUsedException() {
        super(EXCEPTION_MESSAGE);
    }
}
