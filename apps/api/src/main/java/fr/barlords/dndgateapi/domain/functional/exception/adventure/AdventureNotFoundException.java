package fr.barlords.dndgateapi.domain.functional.exception.adventure;

public class AdventureNotFoundException extends RuntimeException{
    public static String EXCEPTION_MESSAGE = "Adventure not found";

    public AdventureNotFoundException() {
        super(EXCEPTION_MESSAGE);
    }

}
