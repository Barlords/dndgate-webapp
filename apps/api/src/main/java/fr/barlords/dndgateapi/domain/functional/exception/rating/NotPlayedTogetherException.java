package fr.barlords.dndgateapi.domain.functional.exception.rating;

public class NotPlayedTogetherException extends RuntimeException {

    public static String EXCEPTION_MESSAGE = "You can only rate someone you have played an adventure with";

    public NotPlayedTogetherException() {
        super(EXCEPTION_MESSAGE);
    }

}
