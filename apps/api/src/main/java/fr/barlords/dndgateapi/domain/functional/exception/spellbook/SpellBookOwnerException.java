package fr.barlords.dndgateapi.domain.functional.exception.spellbook;

public class SpellBookOwnerException extends RuntimeException {
    public static String EXCEPTION_MESSAGE = "You are not the owner of the spell book";

    public SpellBookOwnerException() {
        super(EXCEPTION_MESSAGE);
    }

}
