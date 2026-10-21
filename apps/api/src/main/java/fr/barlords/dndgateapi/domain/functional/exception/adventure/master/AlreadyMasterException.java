package fr.barlords.dndgateapi.domain.functional.exception.adventure.master;

public class AlreadyMasterException extends RuntimeException {

    public static String EXCEPTION_MESSAGE = "You are already the game master of this adventure";

    public AlreadyMasterException() {
        super(EXCEPTION_MESSAGE);
    }

}
