package fr.barlords.dndgateapi.domain.functional.model.account;

import lombok.Builder;
import lombok.Value;
import lombok.With;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

/**
 * A signup that waits for the owner of the address to follow the link of the confirmation mail: no account exists
 * until then, and the pseudo is not reserved by it. Only the hash of the code is kept, the clear code goes in the mail.
 */
@Value
@Builder
public class PendingSignup {

    @Builder.Default
    @With
    UUID id = UUID.randomUUID();

    /** The normalised address (trimmed, lower-cased): one pending signup per address. */
    @With
    String email;

    @With
    String pseudo;

    /** The password, already hashed: the clear one is never kept. */
    @With
    String passwordHash;

    /** SHA-256 of the code sent by e-mail, in hexadecimal. */
    @With
    String codeHash;

    @With
    Instant createdAt;

    /** After this instant the code no longer works and the signup is purged. */
    @With
    Instant expiresAt;

    /** What is stored in place of a code: the code is long and random, so a plain digest is enough. */
    public static String hashOf(String code) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(code.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is always available", e);
        }
    }

}
