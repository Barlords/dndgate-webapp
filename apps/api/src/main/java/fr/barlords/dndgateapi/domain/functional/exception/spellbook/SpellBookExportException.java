package fr.barlords.dndgateapi.domain.functional.exception.spellbook;

public class SpellBookExportException extends RuntimeException{
    public static String EXCEPTION_MESSAGE = "An error occurred while creating the file";

    public SpellBookExportException() {
        super(EXCEPTION_MESSAGE);
    }

}
