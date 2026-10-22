package fr.barlords.dndgateapi.domain.functional.exception;

/**
 * The data was changed by someone else between the moment it was read and the moment it was saved: saving it
 * would overwrite their change. The caller reads again and retries.
 */
public class ConcurrentUpdateException extends RuntimeException {

    public static final String EXCEPTION_MESSAGE = "The data has just been modified by someone else, please try again";

    public ConcurrentUpdateException(Throwable cause) {
        super(EXCEPTION_MESSAGE, cause);
    }

}
