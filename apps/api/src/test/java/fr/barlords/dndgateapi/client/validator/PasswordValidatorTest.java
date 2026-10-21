package fr.barlords.dndgateapi.client.validator;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class PasswordValidatorTest {

    private final PasswordValidator validator = new PasswordValidator();

    private boolean valid(String password) {
        return validator.isValid(password, null);
    }

    @Test
    void a_password_of_at_least_8_characters_is_accepted() {
        Assertions.assertTrue(valid("abcdefgh"));
        Assertions.assertTrue(valid("a much longer pass phrase with spaces"));
    }

    @Test
    void a_password_shorter_than_8_characters_is_refused() {
        Assertions.assertFalse(valid(""));
        Assertions.assertFalse(valid("abcdefg"));
    }

    @Test
    void a_password_of_only_spaces_is_refused() {
        Assertions.assertFalse(valid("        "));
        Assertions.assertFalse(valid("\t\t\t\t\t\t\t\t"));
    }

    @Test
    void spaces_around_the_password_are_part_of_it() {
        Assertions.assertTrue(valid(" secret1 "));
    }

    @Test
    void the_limit_is_72_bytes_not_72_characters() {
        Assertions.assertTrue(valid("a".repeat(72)));
        Assertions.assertFalse(valid("a".repeat(73)));

        // 3 bytes each in UTF-8
        Assertions.assertTrue(valid("€".repeat(24)));
        Assertions.assertFalse(valid("€".repeat(25)));
    }

    @Test
    void length_counts_characters_not_bytes() {
        Assertions.assertTrue(valid("é".repeat(8)));
        Assertions.assertFalse(valid("é".repeat(7)));
    }

    @Test
    void null_is_left_to_not_null() {
        Assertions.assertTrue(valid(null));
    }
}
