package fr.barlords.dndgateapi.domain.functional.service;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

class RandomStringGeneratorTest {

    @Test
    void alphanumeric_codes_have_the_requested_length_and_only_letters_and_digits() {
        for (int i = 0; i < 500; i++) {
            String code = RandomStringGenerator.generateAlphanumericString(10);

            Assertions.assertEquals(10, code.length());
            Assertions.assertTrue(code.matches("[0-9A-Za-z]{10}"), code);
        }
    }

    @Test
    void alphabetic_codes_only_contain_lowercase_letters() {
        for (int i = 0; i < 200; i++) {
            Assertions.assertTrue(RandomStringGenerator.generateAlphabeticString(12).matches("[a-z]{12}"));
        }
    }

    @Test
    void codes_do_not_repeat() {
        Set<String> codes = new HashSet<>();
        for (int i = 0; i < 20_000; i++) {
            codes.add(RandomStringGenerator.generateAlphanumericString(10));
        }

        Assertions.assertEquals(20_000, codes.size());
    }

    @Test
    void every_character_of_the_alphabet_is_reachable_at_every_position() {
        Set<Character> seenAtFirstPosition = new HashSet<>();
        Set<Character> seenAtLastPosition = new HashSet<>();
        for (int i = 0; i < 20_000; i++) {
            String code = RandomStringGenerator.generateAlphanumericString(10);
            seenAtFirstPosition.add(code.charAt(0));
            seenAtLastPosition.add(code.charAt(9));
        }

        Assertions.assertEquals(62, seenAtFirstPosition.size());
        Assertions.assertEquals(62, seenAtLastPosition.size());
    }

    @Test
    void a_zero_length_code_is_empty() {
        Assertions.assertEquals("", RandomStringGenerator.generateAlphanumericString(0));
    }
}
