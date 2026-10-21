package fr.barlords.dndgateapi.domain.functional.exception.adventure.master;

public class MasterSeatNotVacantException extends RuntimeException {

    public static String EXCEPTION_MESSAGE = "The adventure still has its game master";

    public MasterSeatNotVacantException() {
        super(EXCEPTION_MESSAGE);
    }

}
