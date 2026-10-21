package fr.barlords.dndgateapi.domain.functional.exception.adventure.master;

public class NotEligibleToVoteException extends RuntimeException {

    public static String EXCEPTION_MESSAGE = "Only the other players of the adventure can vote";

    public NotEligibleToVoteException() {
        super(EXCEPTION_MESSAGE);
    }

}
