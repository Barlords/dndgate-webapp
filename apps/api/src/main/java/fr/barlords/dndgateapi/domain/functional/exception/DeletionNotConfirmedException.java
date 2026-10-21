package fr.barlords.dndgateapi.domain.functional.exception;

public class DeletionNotConfirmedException extends RuntimeException {

    public static String EXCEPTION_MESSAGE = "The confirmation does not match: nothing was deleted";

    public DeletionNotConfirmedException() {
        super(EXCEPTION_MESSAGE);
    }

}
