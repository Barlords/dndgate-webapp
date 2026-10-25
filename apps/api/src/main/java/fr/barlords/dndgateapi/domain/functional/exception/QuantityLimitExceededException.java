package fr.barlords.dndgateapi.domain.functional.exception;

/** Something cannot be created because its owner already holds the maximum allowed. */
public class QuantityLimitExceededException extends RuntimeException {

    private QuantityLimitExceededException(String message) {
        super(message);
    }

    public static QuantityLimitExceededException characters(int max) {
        return new QuantityLimitExceededException("A player can have at most " + max + " characters in an adventure");
    }

    public static QuantityLimitExceededException spells(int max) {
        return new QuantityLimitExceededException("A spell book can hold at most " + max + " spells");
    }

    public static QuantityLimitExceededException adventureNotes(int max) {
        return new QuantityLimitExceededException("An adventure can have at most " + max + " notes");
    }

    public static QuantityLimitExceededException historicalNotes(int max) {
        return new QuantityLimitExceededException("An adventure can have at most " + max + " historical notes");
    }

}
