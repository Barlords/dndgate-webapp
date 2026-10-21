package fr.barlords.dndgateapi.domain.functional.exception.adventure.master;

public class MasterInvitationNotFoundException extends RuntimeException {

    public static String EXCEPTION_MESSAGE = "This invitation does not exist or is no longer valid";

    public MasterInvitationNotFoundException() {
        super(EXCEPTION_MESSAGE);
    }

}
