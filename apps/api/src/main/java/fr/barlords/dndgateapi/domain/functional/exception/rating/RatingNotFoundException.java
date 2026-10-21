package fr.barlords.dndgateapi.domain.functional.exception.rating;

public class RatingNotFoundException extends RuntimeException{
    public static String EXCEPTION_MESSAGE = "Rating not found";

    public RatingNotFoundException() {
        super(EXCEPTION_MESSAGE);
    }

}
