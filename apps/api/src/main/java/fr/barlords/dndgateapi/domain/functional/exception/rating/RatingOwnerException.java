package fr.barlords.dndgateapi.domain.functional.exception.rating;

public class RatingOwnerException extends RuntimeException {
    public static String EXCEPTION_MESSAGE = "You are not the owner of the rating";

    public RatingOwnerException() {
        super(EXCEPTION_MESSAGE);
    }

}
