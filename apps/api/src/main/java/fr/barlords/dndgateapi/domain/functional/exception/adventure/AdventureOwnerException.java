package fr.barlords.dndgateapi.domain.functional.exception.adventure;

public class AdventureOwnerException extends RuntimeException {
    public static String EXCEPTION_MESSAGE = "You are not the owner of the group";

    public AdventureOwnerException() {
        super(EXCEPTION_MESSAGE);
    }

}
