package fr.barlords.dndgateapi.client.validator;

public interface Patterns {

    /** At least one character that is not a space: a text made of spaces is not a value (for the fields that are optional). */
    String NOT_BLANK = "(?s).*\\S.*";
}
