package fr.barlords.dndgateapi.domain.functional.exception.adventure.master;

public class MasterClaimNotFoundException extends RuntimeException {

    public static String EXCEPTION_MESSAGE = "There is no vote in progress for the game master role";

    public MasterClaimNotFoundException() {
        super(EXCEPTION_MESSAGE);
    }

}
