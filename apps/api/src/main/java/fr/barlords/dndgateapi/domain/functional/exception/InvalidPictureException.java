package fr.barlords.dndgateapi.domain.functional.exception;

public class InvalidPictureException extends RuntimeException {

    public static String EXCEPTION_MESSAGE = "The picture must be a PNG or a JPEG image of 5 MB at most";

    public InvalidPictureException() {
        super(EXCEPTION_MESSAGE);
    }

}
