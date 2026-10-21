package fr.barlords.dndgateapi.domain.functional.exception.rating;

public class RatingAlreadyExistsException extends RuntimeException {

    public static String EXCEPTION_MESSAGE = "You have already rated this account";

    public RatingAlreadyExistsException() {
        super(EXCEPTION_MESSAGE);
    }

}
