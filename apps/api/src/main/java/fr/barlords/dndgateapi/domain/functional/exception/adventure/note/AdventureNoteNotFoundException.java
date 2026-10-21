package fr.barlords.dndgateapi.domain.functional.exception.adventure.note;

public class AdventureNoteNotFoundException extends RuntimeException{
    public static String EXCEPTION_MESSAGE = "Adventure note not found";

    public AdventureNoteNotFoundException() {
        super(EXCEPTION_MESSAGE);
    }

}
