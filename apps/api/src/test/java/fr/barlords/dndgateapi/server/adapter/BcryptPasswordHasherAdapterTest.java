package fr.barlords.dndgateapi.server.adapter;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class BcryptPasswordHasherAdapterTest {

    private final BcryptPasswordHasherAdapter hasher = new BcryptPasswordHasherAdapter();

    @Test
    void a_password_matches_its_own_hash_and_only_that_one() {
        String hash = hasher.hash("correct horse");

        Assertions.assertTrue(hash.startsWith("$2"));
        Assertions.assertTrue(hasher.matches("correct horse", hash));
        Assertions.assertFalse(hasher.matches("correct horse ", hash));
        Assertions.assertFalse(hasher.matches("Correct horse", hash));
    }

    @Test
    void the_same_password_gets_a_different_hash_each_time() {
        Assertions.assertNotEquals(hasher.hash("correct horse"), hasher.hash("correct horse"));
    }

    @Test
    void a_password_longer_than_72_bytes_cannot_be_hashed() {
        Assertions.assertThrows(IllegalArgumentException.class, () -> hasher.hash("a".repeat(73)));
    }

    @Test
    void a_password_longer_than_72_bytes_never_matches_and_does_not_fail() {
        String hash = hasher.hash("a".repeat(72));

        Assertions.assertFalse(hasher.matches("a".repeat(73), hash));
        Assertions.assertFalse(hasher.matches("a".repeat(5000), hash));
    }

    @Test
    void a_missing_hash_never_matches() {
        Assertions.assertFalse(hasher.matches("whatever", null));
    }
}
