package fr.barlords.dndgateapi.domain.functional.exception.adventure.master;

public class MasterClaimAlreadyOpenException extends RuntimeException {

    public static String EXCEPTION_MESSAGE = "A vote for the game master role is already in progress";

    public MasterClaimAlreadyOpenException() {
        super(EXCEPTION_MESSAGE);
    }

}
