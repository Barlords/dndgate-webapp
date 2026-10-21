package fr.barlords.dndgateapi.domain.functional.exception.spellbook;

public class SpellBookNotFoundException extends RuntimeException{
    public static String EXCEPTION_MESSAGE = "Spell book not found";

    public SpellBookNotFoundException() {
        super(EXCEPTION_MESSAGE);
    }

}
