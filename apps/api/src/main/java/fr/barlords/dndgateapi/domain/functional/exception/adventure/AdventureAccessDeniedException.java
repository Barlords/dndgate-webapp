package fr.barlords.dndgateapi.domain.functional.exception.adventure;

public class AdventureAccessDeniedException extends RuntimeException {

    public static String EXCEPTION_MESSAGE = "You are not a participant of this adventure";

    public AdventureAccessDeniedException() {
        super(EXCEPTION_MESSAGE);
    }

}
