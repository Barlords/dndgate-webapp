package fr.barlords.dndgateapi.domain.functional.exception.adventure.master;

public class NotEligibleToClaimMasterException extends RuntimeException {

    public static String EXCEPTION_MESSAGE = "Only a player of the adventure can ask for the game master role";

    public NotEligibleToClaimMasterException() {
        super(EXCEPTION_MESSAGE);
    }

}
