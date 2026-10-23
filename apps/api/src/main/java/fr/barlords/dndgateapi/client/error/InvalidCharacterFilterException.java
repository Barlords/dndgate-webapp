package fr.barlords.dndgateapi.client.error;

/** The character listing was asked with both filters, or with none. */
public class InvalidCharacterFilterException extends RuntimeException {

    public static final String EXCEPTION_MESSAGE = "Give exactly one of ownerId or adventureId";

    public InvalidCharacterFilterException() {
        super(EXCEPTION_MESSAGE);
    }
}
