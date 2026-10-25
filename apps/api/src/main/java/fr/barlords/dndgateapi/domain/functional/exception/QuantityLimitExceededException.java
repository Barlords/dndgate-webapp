package fr.barlords.dndgateapi.domain.functional.exception;

/** Something cannot be created because its owner already holds the maximum allowed. */
public class QuantityLimitExceededException extends RuntimeException {

    private QuantityLimitExceededException(String message) {
        super(message);
    }

    public static QuantityLimitExceededException spells(int max) {
        return new QuantityLimitExceededException("A spell book can hold at most " + max + " spells");
    }

}
