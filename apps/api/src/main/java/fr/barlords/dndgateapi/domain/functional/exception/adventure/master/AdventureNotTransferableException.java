package fr.barlords.dndgateapi.domain.functional.exception.adventure.master;

public class AdventureNotTransferableException extends RuntimeException {

    public static String EXCEPTION_MESSAGE = "The game master role can only change while the adventure is not started or ongoing";

    public AdventureNotTransferableException() {
        super(EXCEPTION_MESSAGE);
    }

}
