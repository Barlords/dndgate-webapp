package fr.barlords.dndgateapi.domain.functional.exception.adventure.note;

public class HistoricalNoteNotFoundException extends RuntimeException{
    public static String EXCEPTION_MESSAGE = "Historical note not found";

    public HistoricalNoteNotFoundException() {
        super(EXCEPTION_MESSAGE);
    }

}
