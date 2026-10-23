package fr.barlords.dndgateapi.domain.functional.exception.magicitem;

public class MagicItemOwnerException extends RuntimeException {
    public static String EXCEPTION_MESSAGE = "You are not the owner of the magic item";

    public MagicItemOwnerException() {
        super(EXCEPTION_MESSAGE);
    }

}
