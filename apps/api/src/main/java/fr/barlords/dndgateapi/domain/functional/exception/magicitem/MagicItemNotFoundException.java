package fr.barlords.dndgateapi.domain.functional.exception.magicitem;

public class MagicItemNotFoundException extends RuntimeException {
    public static String EXCEPTION_MESSAGE = "Magic item not found";

    public MagicItemNotFoundException() {
        super(EXCEPTION_MESSAGE);
    }

}
