package fr.barlords.dndgateapi.domain.functional.exception.adventure;

public class AdventureFullException extends RuntimeException {

    public static String EXCEPTION_MESSAGE = "The adventure is full";

    public AdventureFullException() {
        super(EXCEPTION_MESSAGE);
    }

}
