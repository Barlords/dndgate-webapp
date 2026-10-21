package fr.barlords.dndgateapi.domain.functional.exception;

public class InvalidPageRequestException extends RuntimeException {

    public static String EXCEPTION_MESSAGE = "The page must be 0 or more and the size between 1 and ";

    public InvalidPageRequestException(int maxSize) {
        super(EXCEPTION_MESSAGE + maxSize);
    }

}
