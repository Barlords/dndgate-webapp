package fr.barlords.dndgateapi.domain.functional.service;

import fr.barlords.dndgateapi.domain.functional.exception.DeletionNotConfirmedException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class DeletionConfirmationTest {

    @Test
    void the_exact_name_is_accepted() {
        Assertions.assertDoesNotThrow(() -> DeletionConfirmation.requireName("La quête du soir", "La quête du soir"));
    }

    @Test
    void a_different_name_is_refused() {
        Assertions.assertThrows(DeletionNotConfirmedException.class,
                () -> DeletionConfirmation.requireName("La quête du soir", "La quête du matin"));
    }

    @Test
    void the_case_matters() {
        Assertions.assertThrows(DeletionNotConfirmedException.class,
                () -> DeletionConfirmation.requireName("La quête du soir", "la quête du soir"));
    }

    @Test
    void spaces_around_the_name_do_not_matter_but_spaces_inside_do() {
        Assertions.assertDoesNotThrow(() -> DeletionConfirmation.requireName("La quête", "  La quête \t"));
        Assertions.assertThrows(DeletionNotConfirmedException.class,
                () -> DeletionConfirmation.requireName("La quête", "La  quête"));
    }

    @Test
    void an_accent_typed_as_a_letter_plus_a_combining_mark_is_the_same_name() {
        String composed = "Épée";
        String decomposed = "Épée";

        Assertions.assertNotEquals(composed, decomposed);
        Assertions.assertDoesNotThrow(() -> DeletionConfirmation.requireName(composed, decomposed));
        Assertions.assertDoesNotThrow(() -> DeletionConfirmation.requireName(decomposed, composed));
    }

    @Test
    void an_empty_or_missing_confirmation_is_refused() {
        Assertions.assertThrows(DeletionNotConfirmedException.class, () -> DeletionConfirmation.requireName("Quest", ""));
        Assertions.assertThrows(DeletionNotConfirmedException.class, () -> DeletionConfirmation.requireName("Quest", "   "));
        Assertions.assertThrows(DeletionNotConfirmedException.class, () -> DeletionConfirmation.requireName("Quest", null));
    }

    @Test
    void an_item_without_a_name_cannot_be_deleted_by_typing_nothing() {
        Assertions.assertThrows(DeletionNotConfirmedException.class, () -> DeletionConfirmation.requireName(null, ""));
        Assertions.assertThrows(DeletionNotConfirmedException.class, () -> DeletionConfirmation.requireName(null, null));
    }

    @Test
    void the_explicit_flag_must_be_true() {
        Assertions.assertDoesNotThrow(() -> DeletionConfirmation.requireConfirmed(true));
        Assertions.assertThrows(DeletionNotConfirmedException.class, () -> DeletionConfirmation.requireConfirmed(false));
        Assertions.assertThrows(DeletionNotConfirmedException.class, () -> DeletionConfirmation.requireConfirmed(null));
    }
}
