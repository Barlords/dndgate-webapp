package fr.barlords.dndgateapi.domain.functional.service;

import java.security.SecureRandom;

/**
 * Generates random codes (invitation codes, verification codes).
 * These codes protect access to accounts and to private adventures: they must come from a
 * cryptographically secure generator, never from java.util.Random whose output can be predicted.
 */
public final class RandomStringGenerator {

    private static final String LOWERCASE = "abcdefghijklmnopqrstuvwxyz";

    private static final String ALPHANUMERIC = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";

    private static final SecureRandom RANDOM = new SecureRandom();

    private RandomStringGenerator() {
    }

    public static String generateAlphabeticString(int length) {
        return generate(length, LOWERCASE);
    }

    public static String generateAlphanumericString(int length) {
        return generate(length, ALPHANUMERIC);
    }

    private static String generate(int length, String alphabet) {
        StringBuilder result = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            result.append(alphabet.charAt(RANDOM.nextInt(alphabet.length())));
        }
        return result.toString();
    }

}
