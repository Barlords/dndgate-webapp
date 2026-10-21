package fr.barlords.dndgateapi.client.validator;

import fr.barlords.dndgateapi.client.dto.account.AccountCreationRequest;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class ValidPseudoTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    private boolean valid(String pseudo) {
        return validator.validateProperty(new AccountCreationRequest("test@test.fr", "password123", pseudo), "pseudo").isEmpty();
    }

    @Test
    void letters_digits_and_a_few_symbols_are_accepted() {
        Assertions.assertTrue(valid("Gandalf"));
        Assertions.assertTrue(valid("gandalf_the-grey.2"));
        Assertions.assertTrue(valid("abc"));
        Assertions.assertTrue(valid("a".repeat(30)));
    }

    @Test
    void letters_of_any_language_are_accepted() {
        Assertions.assertTrue(valid("José"));
        Assertions.assertTrue(valid("Zoë"));
        Assertions.assertTrue(valid("Müller"));
        Assertions.assertTrue(valid("李雷雷"));
        Assertions.assertTrue(valid("Ελένη"));
    }

    @Test
    void the_spaces_around_are_tolerated_because_they_are_removed_afterwards() {
        Assertions.assertTrue(valid("  Gandalf "));
    }

    @Test
    void the_length_counts_without_the_spaces_around() {
        Assertions.assertFalse(valid("  ab  "), "2 characters once trimmed");
        Assertions.assertFalse(valid("ab"));
        Assertions.assertFalse(valid("a".repeat(31)));
        Assertions.assertFalse(valid(" ".repeat(10) + "a".repeat(31) + " ".repeat(10)));
        Assertions.assertFalse(valid(" ".repeat(100) + "abc"), "absurd padding");
    }

    @Test
    void markup_is_refused() {
        Assertions.assertFalse(valid("<b>bob</b>"));
        Assertions.assertFalse(valid("<script>alert(1)</script>"));
        Assertions.assertFalse(valid("bob<"));
        Assertions.assertFalse(valid("\"onmouseover=\"x"));
        Assertions.assertFalse(valid("bob'; drop table accounts;--"));
    }

    @Test
    void spaces_inside_and_control_characters_are_refused() {
        Assertions.assertFalse(valid("bob smith"));
        Assertions.assertFalse(valid("bob\nsmith"));
        Assertions.assertFalse(valid("bob\r\nBcc: victim"));
        Assertions.assertFalse(valid("bob\tsmith"));
        Assertions.assertFalse(valid("bob\u0000"));
    }

    @Test
    void symbols_that_have_a_meaning_somewhere_are_refused() {
        for (String symbol : new String[]{"@", "#", "$", "%", "&", "*", "(", ")", "/", "\\", ":", ";", "=", "?", "+", ",", "[", "]", "{", "}", "|", "~", "`", "!"}) {
            Assertions.assertFalse(valid("bob" + symbol), symbol);
        }
    }

    @Test
    void an_empty_or_blank_pseudo_is_refused() {
        Assertions.assertFalse(valid(""));
        Assertions.assertFalse(valid("      "));
    }
}
