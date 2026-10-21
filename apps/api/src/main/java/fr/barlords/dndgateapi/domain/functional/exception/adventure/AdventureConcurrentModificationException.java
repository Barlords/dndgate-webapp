package fr.barlords.dndgateapi.domain.functional.exception.adventure;

/** The adventure was changed by someone else between the moment it was read and the moment it was saved. */
public class AdventureConcurrentModificationException extends RuntimeException {

    public static String EXCEPTION_MESSAGE = "The adventure has just been modified by someone else, please try again";

    public AdventureConcurrentModificationException(Throwable cause) {
        super(EXCEPTION_MESSAGE, cause);
    }

}
