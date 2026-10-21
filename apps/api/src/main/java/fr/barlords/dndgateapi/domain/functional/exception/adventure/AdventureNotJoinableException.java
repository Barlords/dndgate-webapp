package fr.barlords.dndgateapi.domain.functional.exception.adventure;

public class AdventureNotJoinableException extends RuntimeException {

    public static String EXCEPTION_MESSAGE = "The adventure has already started or ended and no longer accepts players";

    public AdventureNotJoinableException() {
        super(EXCEPTION_MESSAGE);
    }

}
