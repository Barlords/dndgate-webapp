package fr.barlords.dndgateapi.domain.functional.service;

import fr.barlords.dndgateapi.domain.functional.exception.DeletionNotConfirmedException;

import java.text.Normalizer;

/**
 * Rule: an irreversible deletion needs an explicit confirmation, checked here and not only by the page.
 * To delete something that has a name, the caller must retype that name.
 */
public final class DeletionConfirmation {

    private DeletionConfirmation() {
    }

    /**
     * The name is compared as typed: same letters, same case. Spaces around it and the way accents are
     * encoded (a single character or a letter plus a combining accent) do not matter.
     */
    public static void requireName(String expectedName, String confirmation) {
        if (expectedName == null || confirmation == null || !normalize(expectedName).equals(normalize(confirmation))) {
            throw new DeletionNotConfirmedException();
        }
    }

    /** For what has no name (notes): an explicit "yes, delete it". */
    public static void requireConfirmed(Boolean confirmed) {
        if (!Boolean.TRUE.equals(confirmed)) {
            throw new DeletionNotConfirmedException();
        }
    }

    private static String normalize(String text) {
        return Normalizer.normalize(text, Normalizer.Form.NFC).strip();
    }

}
