package fr.barlords.dndgateapi.client.mapper;

public interface Text {

    /** Removes the spaces around a text that may be absent (a field that a patch does not change). */
    static String trimmed(String text) {
        return text == null ? null : text.trim();
    }
}
