package fr.barlords.dndgateapi.domain.functional.exception.adventure;

public class AdventureInProgressException extends RuntimeException {

    public static String EXCEPTION_MESSAGE = "An adventure in progress cannot be deleted: finish or abort it first";

    public AdventureInProgressException() {
        super(EXCEPTION_MESSAGE);
    }

}
