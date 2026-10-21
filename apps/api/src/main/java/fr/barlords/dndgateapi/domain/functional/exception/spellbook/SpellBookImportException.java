package fr.barlords.dndgateapi.domain.functional.exception.spellbook;

public class SpellBookImportException extends RuntimeException {
    public static String EXCEPTION_MESSAGE = "An error occurred while reading the file";

    public SpellBookImportException() {
        super(EXCEPTION_MESSAGE);
    }

}
