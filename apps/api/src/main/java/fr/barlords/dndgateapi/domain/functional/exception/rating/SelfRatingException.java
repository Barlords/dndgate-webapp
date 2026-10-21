package fr.barlords.dndgateapi.domain.functional.exception.rating;

public class SelfRatingException extends RuntimeException {

    public static String EXCEPTION_MESSAGE = "You cannot rate yourself";

    public SelfRatingException() {
        super(EXCEPTION_MESSAGE);
    }

}
